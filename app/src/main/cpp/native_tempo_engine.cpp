#include <jni.h>

#include <algorithm>
#include <cmath>
#include <map>
#include <numeric>
#include <utility>
#include <vector>

namespace {

constexpr int kFrameSize = 1024;
constexpr int kHopSize = 512;
constexpr int kMaxReturnCandidates = 5;

struct Candidate {
    double bpm;
    double score;
};

struct ScoreRow {
    double bpm;
    double full_score;
    double low_score;
    double peak_score;
    double score;
};

std::vector<double> moving_average(const std::vector<double>& values, int window) {
    if (values.empty()) return {};
    std::vector<double> output(values.size(), 0.0);
    double sum = 0.0;
    for (size_t i = 0; i < values.size(); ++i) {
        sum += values[i];
        if (static_cast<int>(i) >= window) {
            sum -= values[i - window];
        }
        const int divisor = std::min(static_cast<int>(i) + 1, window);
        output[i] = sum / std::max(1, divisor);
    }
    return output;
}

std::vector<double> positive_normalize(const std::vector<double>& values) {
    if (values.empty()) return {};
    const double mean = std::accumulate(values.begin(), values.end(), 0.0) / values.size();
    double variance = 0.0;
    for (double value : values) {
        const double centered = value - mean;
        variance += centered * centered;
    }
    variance /= values.size();
    const double stddev = std::sqrt(variance);

    std::vector<double> output(values.size(), 0.0);
    if (stddev < 0.000001) {
        const auto [min_it, max_it] = std::minmax_element(values.begin(), values.end());
        const double range = *max_it - *min_it;
        if (range < 0.000001) return output;
        for (size_t i = 0; i < values.size(); ++i) {
            output[i] = std::max(0.0, (values[i] - *min_it) / range);
        }
        return output;
    }

    for (size_t i = 0; i < values.size(); ++i) {
        output[i] = std::max(0.0, (values[i] - mean) / stddev);
    }
    return output;
}

std::vector<double> energy_envelope(const std::vector<float>& samples, int sample_rate) {
    const int frame_count = std::max(0, static_cast<int>(samples.size()) - kFrameSize) / kHopSize + 1;
    std::vector<double> energies(frame_count, 0.0);

    for (int frame = 0; frame < frame_count; ++frame) {
        const int start = frame * kHopSize;
        const int end = std::min(start + kFrameSize, static_cast<int>(samples.size()));
        double sum = 0.0;
        for (int i = start; i < end; ++i) {
            sum += static_cast<double>(samples[i]) * samples[i];
        }
        energies[frame] = std::sqrt(sum / std::max(1, end - start));
    }

    const int window = std::max(3, (sample_rate / kHopSize) / 4);
    return positive_normalize(moving_average(energies, window));
}

std::vector<double> low_band_envelope(const std::vector<float>& samples, int sample_rate) {
    const int frame_count = std::max(0, static_cast<int>(samples.size()) - kFrameSize) / kHopSize + 1;
    std::vector<double> energies(frame_count, 0.0);
    if (sample_rate <= 0 || frame_count <= 0) return energies;

    constexpr double cutoff_hz = 180.0;
    const double alpha = std::exp(-2.0 * M_PI * cutoff_hz / sample_rate);
    double low = 0.0;

    for (int frame = 0; frame < frame_count; ++frame) {
        const int start = frame * kHopSize;
        const int end = std::min(start + kFrameSize, static_cast<int>(samples.size()));
        double sum = 0.0;
        for (int i = start; i < end; ++i) {
            low = (1.0 - alpha) * static_cast<double>(samples[i]) + alpha * low;
            sum += low * low;
        }
        energies[frame] = std::sqrt(sum / std::max(1, end - start));
    }

    const int window = std::max(3, (sample_rate / kHopSize) / 4);
    return positive_normalize(moving_average(energies, window));
}

std::vector<double> onset_envelope(const std::vector<float>& samples, int sample_rate) {
    const auto energy = energy_envelope(samples, sample_rate);
    std::vector<double> flux(energy.size(), 0.0);
    for (size_t i = 1; i < energy.size(); ++i) {
        flux[i] = std::max(0.0, energy[i] - energy[i - 1]);
    }
    const int window = std::max(3, (sample_rate / kHopSize) / 3);
    return positive_normalize(moving_average(flux, window));
}

std::vector<double> combined_envelope(const std::vector<float>& samples, int sample_rate) {
    const auto onset = onset_envelope(samples, sample_rate);
    const auto energy = energy_envelope(samples, sample_rate);
    const size_t size = std::min(onset.size(), energy.size());
    std::vector<double> combined(size, 0.0);
    for (size_t i = 0; i < size; ++i) {
        combined[i] = std::max(onset[i], energy[i] * 0.45);
    }
    return positive_normalize(combined);
}

double autocorrelation_score(const std::vector<double>& values, int lag) {
    if (lag <= 0 || lag >= static_cast<int>(values.size())) return 0.0;
    double score = 0.0;
    double norm_a = 0.0;
    double norm_b = 0.0;
    int count = 0;
    for (int i = lag; i < static_cast<int>(values.size()); ++i) {
        const double a = values[i];
        const double b = values[i - lag];
        score += a * b;
        norm_a += a * a;
        norm_b += b * b;
        ++count;
    }
    if (count == 0 || norm_a <= 0.0 || norm_b <= 0.0) return 0.0;
    return score / std::sqrt(norm_a * norm_b);
}

double rhythm_score(
    const std::vector<double>& values,
    int lag,
    double second_weight,
    double third_weight
) {
    return autocorrelation_score(values, lag) +
        second_weight * autocorrelation_score(values, lag * 2) +
        third_weight * autocorrelation_score(values, lag * 3);
}

double fold_bpm(double bpm, int min_bpm, int max_bpm) {
    while (bpm < min_bpm) bpm *= 2.0;
    while (bpm > max_bpm) bpm /= 2.0;
    return bpm;
}

double tempo_prior(double bpm) {
    if (bpm < 55.0) return 0.35;
    if (bpm < 68.0) return 0.65;
    if (bpm <= 150.0) return 1.0;
    if (bpm <= 159.0) return 0.92;
    if (bpm <= 182.0) return 0.96;
    if (bpm <= 200.0) return 0.62;
    return 0.35;
}

double selection_weight(double bpm) {
    if (bpm <= 184.0) return 1.0;
    if (bpm <= 200.0) return 0.72;
    return 0.5;
}

double subdivision_penalty(const ScoreRow& row, const std::map<int, ScoreRow>& rows_by_bpm) {
    if (row.bpm < 185.0) return 1.0;
    const int half = static_cast<int>(std::round(row.bpm / 2.0));
    const auto half_it = rows_by_bpm.find(half);
    if (half_it == rows_by_bpm.end()) return 1.0;

    const ScoreRow& half_row = half_it->second;
    const double half_combined =
        half_row.full_score + 0.18 * half_row.low_score + 0.08 * half_row.peak_score;
    const bool half_is_competitive = half_combined >= row.score * 0.68;
    const bool half_has_stronger_low_pulse = half_row.low_score >= row.low_score * 1.08;
    if (half_is_competitive && half_has_stronger_low_pulse) return 0.68;
    if (row.bpm >= 190.0 && half_is_competitive) return 0.82;
    return 1.0;
}

std::vector<Candidate> peak_interval_candidates(
    const std::vector<double>& envelope,
    double onset_rate,
    int min_bpm,
    int max_bpm
) {
    if (envelope.size() < 8) return {};
    const double mean = std::accumulate(envelope.begin(), envelope.end(), 0.0) / envelope.size();
    double mean_square = 0.0;
    for (double value : envelope) mean_square += value * value;
    mean_square /= envelope.size();
    const double threshold = mean + std::sqrt(mean_square) * 0.35;

    std::vector<int> peaks;
    for (int i = 1; i < static_cast<int>(envelope.size()) - 1; ++i) {
        if (envelope[i] >= threshold && envelope[i] >= envelope[i - 1] && envelope[i] >= envelope[i + 1]) {
            peaks.push_back(i);
        }
    }
    if (peaks.size() < 3) return {};

    std::map<int, int> counts;
    for (size_t i = 1; i < peaks.size(); ++i) {
        const int interval = peaks[i] - peaks[i - 1];
        if (interval <= 0) continue;
        const double bpm = fold_bpm(onset_rate * 60.0 / interval, min_bpm, max_bpm);
        const int rounded = static_cast<int>(std::round(bpm));
        if (rounded >= min_bpm && rounded <= max_bpm) {
            counts[rounded] += 1;
        }
    }

    int max_count = 0;
    for (const auto& entry : counts) max_count = std::max(max_count, entry.second);
    if (max_count == 0) return {};

    std::vector<Candidate> candidates;
    for (const auto& entry : counts) {
        candidates.push_back({static_cast<double>(entry.first), entry.second / static_cast<double>(max_count) * 0.65});
    }
    std::sort(candidates.begin(), candidates.end(), [](const Candidate& a, const Candidate& b) {
        return a.score > b.score;
    });
    return candidates;
}

std::vector<Candidate> estimate_tempo(
    const std::vector<float>& samples,
    int sample_rate,
    int min_bpm,
    int max_bpm
) {
    if (sample_rate <= 0 || samples.size() < static_cast<size_t>(sample_rate * 4)) return {};

    const auto envelope = combined_envelope(samples, sample_rate);
    if (envelope.size() < 8) return {};

    const double onset_rate = sample_rate / static_cast<double>(kHopSize);
    const auto low_envelope = low_band_envelope(samples, sample_rate);
    std::vector<ScoreRow> rows;

    for (double bpm = min_bpm; bpm <= max_bpm; bpm += 0.5) {
        const int lag = static_cast<int>(std::round(onset_rate * 60.0 / bpm));
        if (lag < 1 || lag >= static_cast<int>(envelope.size()) / 2) continue;

        const double full_score = rhythm_score(envelope, lag, 0.42, 0.20);
        const double low_score = rhythm_score(low_envelope, lag, 0.35, 0.15);
        const double peak_score = 0.0;
        const double score = full_score + 0.18 * low_score + 0.08 * peak_score;

        if (std::isfinite(score) && score > 0.0) {
            rows.push_back({bpm, full_score, low_score, peak_score, score});
        }
    }

    if (rows.empty()) return peak_interval_candidates(envelope, onset_rate, min_bpm, max_bpm);

    std::map<int, ScoreRow> rows_by_bpm;
    for (const auto& row : rows) {
        rows_by_bpm[static_cast<int>(std::round(row.bpm))] = row;
    }
    std::vector<Candidate> scored;
    scored.reserve(rows.size());
    for (const auto& row : rows) {
        const double score =
            row.score * tempo_prior(row.bpm) * selection_weight(row.bpm) * subdivision_penalty(row, rows_by_bpm);
        scored.push_back({row.bpm, score});
    }

    const double max_score = std::max(0.000001, std::max_element(
        scored.begin(),
        scored.end(),
        [](const Candidate& a, const Candidate& b) { return a.score < b.score; }
    )->score);

    std::vector<Candidate> local_maxima;
    for (size_t i = 0; i < scored.size(); ++i) {
        const double previous = i > 0 ? scored[i - 1].score : -1.0;
        const double next = i + 1 < scored.size() ? scored[i + 1].score : -1.0;
        if (scored[i].score >= previous && scored[i].score >= next) {
            local_maxima.push_back({scored[i].bpm, std::min(1.0, scored[i].score / max_score)});
        }
    }

    auto peak_candidates = peak_interval_candidates(envelope, onset_rate, min_bpm, max_bpm);
    for (const auto& candidate : peak_candidates) {
        local_maxima.push_back({candidate.bpm, candidate.score * 0.85});
    }

    std::sort(local_maxima.begin(), local_maxima.end(), [](const Candidate& a, const Candidate& b) {
        return a.score > b.score;
    });

    std::vector<Candidate> selected;
    for (const auto& candidate : local_maxima) {
        const bool covered = std::any_of(selected.begin(), selected.end(), [&](const Candidate& existing) {
            return std::abs(existing.bpm - candidate.bpm) < 2.0 ||
                std::abs(existing.bpm * 2.0 - candidate.bpm) < 2.0 ||
                std::abs(existing.bpm - candidate.bpm * 2.0) < 2.0;
        });
        if (!covered) selected.push_back(candidate);
        if (selected.size() >= kMaxReturnCandidates) break;
    }

    return selected;
}

}  // namespace

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_example_integratedbpmmeter_audio_NativeTempoEngine_analyzeNative(
    JNIEnv* env,
    jobject,
    jfloatArray samples_array,
    jint sample_rate,
    jint min_bpm,
    jint max_bpm
) {
    const jsize size = env->GetArrayLength(samples_array);
    std::vector<float> samples(size);
    env->GetFloatArrayRegion(samples_array, 0, size, samples.data());

    const auto candidates = estimate_tempo(samples, sample_rate, min_bpm, max_bpm);
    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(candidates.size() * 2));
    if (result == nullptr) return nullptr;

    std::vector<double> values;
    values.reserve(candidates.size() * 2);
    for (const auto& candidate : candidates) {
        values.push_back(candidate.bpm);
        values.push_back(candidate.score);
    }

    if (!values.empty()) {
        env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    }
    return result;
}
