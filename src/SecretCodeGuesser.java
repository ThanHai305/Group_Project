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
     * <p>
     * This method incrementally probes candidate strings composed solely of 'B's, increasing their
     * length one by one until the evaluator no longer reports a length mismatch. The length at which
     * the evaluator accepts the guess defines the true secret code length. During this process, the
     * evaluator’s feedback also reveals how many 'B's are present in the actual code, which is recorded
     * in the provided frequency table.
     * </p>
     *
     * <h3>Complexity</h3>
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
     * Refines the current candidate string one position at a time until
     * the entire secret code is discovered.
     *
     * <p>The method works by starting with a baseline guess that is built using blocks of characters
     * sorted by descending frequency. From this baseline, it attempts to replace characters at uncertain
     * positions with other possible letters, guided by both frequency priority and pruning rules.</p>
     *
     * <h3>Core Idea</h3>
     * <p>
     * Start with a baseline guess which is a string filled with the most common character (the one with the highest frequency).
     * Then go through all other possible characters in order from most common to least common and check whether it can be used
     * to replace any of the characters in the baseline guess until it has correctly guessed all positions in the secret key except
     * for the last incorrect one. This last incorrect one is simply the character with the smallest remaining non-zero frequency.
     * </p>
     *
     * <h3>Optimizations</h3>
     * <ul>
     *   <li>Forced Fill: If a letter’s remaining count equals the number of unconfirmed positions, fill them directly without testing.
     *   Parts of the initial draft of this algorithm were generated using Grok AI (as of August 2025). Prompts such as “Is there any way to make less guess? Like improve the worst-case
     *   scenario? Like if full B, A, C, X, I score = 0, then just add that whole array with U, like automatically fill every space with U since there’s no other characters?” were used.
     *   All output was verified, edited, and adapted by <Hoc Tran>. The full AI interaction log is included in the Appendix.
     *   </li>
     *
     *   <li>Global Priority: Try replacement letters in order of their remaining frequency (most likely first).</li>
     *   <li>Exhaustion Tracking: Skip letters that are already fully placed (remaining count ≤ 0).</li>
     *   <li>Bitmask Pruning: Maintain masks of allowed positions for each letter to avoid retesting eliminated placements.</li>
     *
     *   <li>Parts of the initial draft of 3 algorithms above were generated using ChatGPT 4 in VS code (as of August 2025). Prompts such as
     *   “how can I improve the guesser code? given the fact that length of secret code is unknown, and you can not modify
     *   the SecretCode.java” were used. All output was verified, edited, and adapted by <Hoc Tran>. The full AI interaction
     *   log is included in the Appendix.</li>
     * </ul>
     *
     * <h3>Complexity</h3>
     * <ul>
     *   <li>Time complexity: O(n²)</li>
     *   <li>Space complexity: O(n)</li>
     *   <li>Guess complexity: O(n²)</li>
     * </ul>
     *
     * @param confirmed boolean array tracking which positions are finalized
     * @param candidate working candidate string
     * @param remaining count of how many occurrences of each letter remain to be placed
     * @param posMask bitmask of allowable positions for each letter
     * @param baseline initial number of matches from the baseline guess
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

                    // Skip characters with the remaining <= 0
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
                    continue;
                }

                for (int k = 0; k < t && !progressed; k++) {
                    int li = tryCandidates[k];
                    char test = ALPH[li];

                    if (remaining[li] <= 0) {
                        continue;
                    }

                    char[] temp = candidate.clone();
                    temp[pos] = test;
                    int mt = callGuess(new String(temp));
                    if (found) return;
                    int delta = mt - baselineMatches;

                    if (delta == 1) {
                        candidate[pos] = test;
                        confirmed[pos] = true;
                        if (remaining[li] > 0) remaining[li]--;
                        int bit = 1 << pos;
                        for (int z = 0; z < ALPH_SZ; z++) if (z != li) posMask[z] &= ~bit;
                        baselineMatches = mt;
                        progressed = true;
                        globalPriority = orderByCountsDesc(remaining);
                        break;
                    } else if (delta == -1) {
                        int origIdx = alphaIndex(candidate[pos]);
                        if (origIdx >= 0 && remaining[origIdx] > 0) remaining[origIdx]--;
                        confirmed[pos] = true;
                        int bit = 1 << pos;
                        for (int z = 0; z < ALPH_SZ; z++) if (z != origIdx) posMask[z] &= ~bit;
                        progressed = true;
                        globalPriority = orderByCountsDesc(remaining);
                        break;
                    } else {
                        posMask[li] &= ~(1 << pos);
                    }
                }
            }

            if (!progressed) {
                break;
            }
        }
    }

    // ============================================================
    // Utility Methods
    // ============================================================

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

    /**
     * Calls {@link SecretCode#guess(String)} and updates
     * {@code found} if the guess is correct.
     *
     * @param s candidate guess
     * @return number of matches reported by the harness
     */
    private int callGuess(String s) {
        int res = harness.guess(s);
        if (res == s.length()) {
            System.out.println("Secret code is found: " + s);
            found = true;
        }
        return res;
    }
}