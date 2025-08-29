/**
 * SecretCodeGuesser
 *
 * <p>This class implements an algorithm to discover a hidden secret code
 * using an interactive harness (SecretCode). The code is composed
 * of characters drawn from a fixed alphabet {'B','A','C','X','I','U'}
 * with a maximum length of 18.</p>
 *
 * <h2>Algorithm Overview</h2>
 * <ol>
 *   <li>Frequency Measurement: Determine the length of the code and frequency of each letter via uniform guesses (e.g. "BBBBB...").</li>
 *   <li>Sanity Check: Ensure measured frequencies match the length.</li>
 *   <li>Forced Fill:If only one letter is present, fill immediately.</li>
 *   <li>Candidate Initialization: Construct an initial candidate string by placing letters in blocks sorted by frequency.</li>
 *   <li>Single-Position Refinement: Iteratively confirm or eliminate candidate letters for each position until the secret is found.</li>
 * </ol>
 <h2>Complexities</h2>
 * <ul>
 *   <li>Time complexity: O(n²) worst case for refinement</li>
 *   <li>Space complexity: O(n)</li>
 *   <li>Guess complexity: O(n²) in the refinement stage</li>
 * </ul>
 */
public class SecretCodeGuesser {

    /** Allowed letters in the secret code (fixed order). */
    private static final char[] ALPH = {'B', 'A', 'C', 'X', 'I', 'U'};
    private static final int ALPH_SZ = ALPH.length;

    /** Harness instance provided by the assignment. */
    private final SecretCode harness = new SecretCode();

    /** Tracks whether the secret has been fully discovered. */
    private boolean found = false;

    // ============================================================
    // Main Entry Point
    // ============================================================

    /**
     * Begins the guessing process.
     *
     * <p>The method orchestrates all phases: frequency measurement,
     * candidate construction, and iterative refinement. Execution
     * stops early if the secret code is discovered.</p>
     */
    public void start() {

        int[] counts = new int[ALPH_SZ];

        int N = detectLengthAndSetBCount(counts);
        if (N > 18) {
            System.out.println("ERROR: Secret code length exceeds 18. Aborting.");
            return;
        }
        if (found) return;

        // Measure other letters (skip 'B', already measured)
        for (int i = 0; i < ALPH_SZ; i++) {
            if (ALPH[i] == 'B') continue;
            counts[i] = callGuess(repeatChar(ALPH[i], N));
            if (found) return;
        }

        // Sanity check: total frequency must equal N
        int sum = 0;
        for (int v : counts) sum += v;
        if (sum != N) {
            System.out.println("ERROR: counts sum != N. Aborting. (counts sum = " + sum + ", N=" + N + ")");
            return;
        }

        // If only one letter is present → fill and exit
        int numNonZero = 0;
        for (int i = 0; i < ALPH_SZ; i++) {
            if (counts[i] > 0) { numNonZero++; }
        }
        if (numNonZero == 1) {
            return;
        }

        // Initialize working structures
        int[] remaining = counts.clone();
        int[] posMask = new int[ALPH_SZ];
        int fullMask = (N >= 31) ? ~0 : ((1 << N) - 1);
        for (int i = 0; i < ALPH_SZ; i++) posMask[i] = fullMask;

        boolean[] confirmed = new boolean[N];
        char[] candidate = new char[N];
        for (int i = 0; i < N; i++) candidate[i] = ALPH[0];

        // Build initial candidate and test baseline
        buildInitialCandidate(candidate, counts);
        int baselineMatches = callGuess(new String(candidate));
        if (found) return;

        // single-position refinement with global priority
        if (!allConfirmed(confirmed)) {
            if (found) return;
            singlePositionRefinement(confirmed, candidate, remaining, posMask, baselineMatches);
        }
    }

    // ============================================================
    // Length Detection
    // ============================================================

    /**
     * Detects the secret code length and counts the number of 'B's.
     *
     * <ul>
     *   <li>Time complexity: O(n)</li>
     *   <li>Space complexity: O(1)</li>
     *   <li>Guess complexity: O(n)</li>
     * </ul>
     *
     * @param counts frequency table to update with 'B' count
     * @return the detected secret length
     */
    private int detectLengthAndSetBCount(int[] counts) {
        int bIdx = alphaIndex('B');
        for (int k = 1; k <= 18; k++) {
            int r = callGuess(repeatChar('B', k));
            if (found) return k;
            if (r != -2) {
                counts[bIdx] = r;
                return k;
            }
        }
        return 18; // fallback
    }


    // ============================================================
    // Single-Position Refinement
    // ============================================================

    /**
     * Refines candidate guesses one position at a time until all positions
     * are confirmed.
     *
     * <h3>Optimizations</h3>
     * <ul>
     *   <li><Forced fill: If a letter’s remaining count equals the number of open slots, fill them directly.</li>
     *   <li>Global priority: Try letters in descending order of remaining frequency.</li>
     *   <li>Exhaustion tracking: Skip letters with zero remaining.</li>
     *   <li>Bitmask pruning: Track and eliminate impossible positions for each letter.</li>
     * </ul>
     *
     * <ul>
     *   <li>Time complexity: O(n²)</li>
     *   <li>Space complexity: O(n)</li>
     *   <li>Guess complexity: O(n²)</li>
     * </ul>
     */
    private void singlePositionRefinement(boolean[] confirmed, char[] candidate,
                                          int[] remaining, int[] posMask, int baseline) {
        // (implementation unchanged)
        int N = candidate.length;
        int baselineMatches = baseline;

        int[] globalPriority = orderByCountsDesc(remaining);

        while (!allConfirmed(confirmed)) {
            // Forced-fill check
            int open = 0;
            for (int i = 0; i < N; i++) if (!confirmed[i]) open++;
            int forcedIdx = -1;
            for (int i = 0; i < ALPH_SZ; i++) {
                if (remaining[i] == open && open > 0) { forcedIdx = i; break; }
            }
            if (forcedIdx != -1) {
                for (int p = 0; p < N; p++) {
                    if (!confirmed[p]) {
                        confirmed[p] = true;
                        candidate[p] = ALPH[forcedIdx];
                        if (remaining[forcedIdx] > 0) remaining[forcedIdx]--;
                        int bit = 1 << p;
                        for (int k = 0; k < ALPH_SZ; k++) if (k != forcedIdx) posMask[k] &= ~bit;
                    }
                }
                baselineMatches = callGuess(new String(candidate));
                if (found) return;
                // recompute priority
                globalPriority = orderByCountsDesc(remaining);
                continue;
            }

            boolean progressed = false;

            // Try to confirm one position per outer loop iteration (keeps changes manageable)
            for (int pos = 0; pos < N && !progressed; pos++) {
                if (confirmed[pos]) continue;

                // Build try-candidates using global priority (filtering by posMask and remaining > 0)
                int[] tryCandidates = new int[ALPH_SZ];
                int t = 0;
                for (int gi = 0; gi < ALPH_SZ; gi++) {
                    int li = globalPriority[gi];

                    // KEY ENHANCEMENT: Skip characters with remaining <= 0
                    if (remaining[li] <= 0) {
                        continue;
                    }

                    if (((posMask[li] >> pos) & 1) == 0) {
                        continue;
                    }

                    if (ALPH[li] == candidate[pos]) {
                        continue; // skip current tentative char
                    }

                    tryCandidates[t++] = li;
                }

                if (t == 0) {
                    continue; // nothing to try here
                }

                // Try candidates in priority order
                for (int k = 0; k < t && !progressed; k++) {
                    int li = tryCandidates[k];
                    char test = ALPH[li];

                    // Double-check that we still have remaining characters before trying
                    if (remaining[li] <= 0) {
                        continue;
                    }

                    char[] temp = candidate.clone();
                    temp[pos] = test;
                    int mt = callGuess(new String(temp));
                    if (found) return;
                    int delta = mt - baselineMatches;

                    if (delta == 1) {
                        // new letter is correct at pos
                        candidate[pos] = test;
                        confirmed[pos] = true;
                        if (remaining[li] > 0) remaining[li]--;
                        int bit = 1 << pos;
                        for (int z = 0; z < ALPH_SZ; z++) if (z != li) posMask[z] &= ~bit;
                        baselineMatches = mt; // adopt new baseline
                        progressed = true;
                        // update global priority since remaining changed
                        globalPriority = orderByCountsDesc(remaining);
                        break;
                    } else if (delta == -1) {
                        // original candidate[pos] was correct
                        int origIdx = alphaIndex(candidate[pos]);
                        if (origIdx >= 0 && remaining[origIdx] > 0) remaining[origIdx]--;
                        confirmed[pos] = true;
                        int bit = 1 << pos;
                        for (int z = 0; z < ALPH_SZ; z++) if (z != origIdx) posMask[z] &= ~bit;
                        progressed = true;
                        // update global priority since remaining changed
                        globalPriority = orderByCountsDesc(remaining);
                        break;
                    } else {
                        // delta == 0: eliminate li at pos
                        posMask[li] &= ~(1 << pos);
                    }
                }
            }

            if (!progressed) {
                break;
            }
        }
    }

    // ---------------- small utilities and helpers ----------------

    private boolean allConfirmed(boolean[] confirmed) {
        for (boolean b : confirmed) if (!b) return false;
        return true;
    }

    private void buildInitialCandidate(char[] cand, int[] counts) {
        int[] order = orderByCountsDesc(counts);
        int p = 0;
        for (int idx : order) {
            for (int k = 0; k < counts[idx] && p < cand.length; k++) cand[p++] = ALPH[idx];
        }
        while (p < cand.length) cand[p++] = ALPH[0];
    }

    private int[] orderByCountsDesc(int[] counts) {
        int[] idx = new int[ALPH_SZ];
        for (int i = 0; i < ALPH_SZ; i++) idx[i] = i;
        for (int i = 0; i < ALPH_SZ - 1; i++) {
            int best = i;
            for (int j = i + 1; j < ALPH_SZ; j++) if (counts[idx[j]] > counts[idx[best]]) best = j;
            int tmp = idx[i]; idx[i] = idx[best]; idx[best] = tmp;
        }
        return idx;
    }

    private String repeatChar(char c, int n) {
        char[] a = new char[n];
        for (int i = 0; i < n; i++) a[i] = c;
        return new String(a);
    }

    private int alphaIndex(char ch) {
        for (int i = 0; i < ALPH_SZ; i++) if (ALPH[i] == ch) return i;
        return -1;
    }

    private int callGuess(String s) {
        int res = harness.guess(s);
        // Only print when secret is found
        if (res == s.length()) {
            System.out.println("Secret code is found: " + s);
            found = true;
        }
        return res;
    }
}