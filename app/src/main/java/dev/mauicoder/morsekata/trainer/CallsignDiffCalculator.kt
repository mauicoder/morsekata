package dev.mauicoder.morsekata.trainer

object CallsignDiffCalculator {

    fun calculate(typed: String, expected: String): CallsignDiffResult {
        val cleanTyped = typed.trim()
        val cleanExpected = expected.trim()

        if (cleanTyped.equals(cleanExpected, ignoreCase = true)) {
            val segments = cleanExpected.uppercase().map { char ->
                DiffSegment(char = char, isCorrect = true)
            }
            return CallsignDiffResult(
                typed = typed,
                expected = expected,
                isMatch = true,
                diffSegments = segments
            )
        }

        val s1 = cleanTyped.uppercase()
        val s2 = cleanExpected.uppercase()

        if (s1.isEmpty()) {
            val segments = s2.map { char ->
                DiffSegment(char = char, isCorrect = false)
            }
            return CallsignDiffResult(
                typed = typed,
                expected = expected,
                isMatch = false,
                diffSegments = segments
            )
        }

        if (s2.isEmpty()) {
            val segments = s1.map { char ->
                DiffSegment(char = char, isCorrect = false)
            }
            return CallsignDiffResult(
                typed = typed,
                expected = expected,
                isMatch = false,
                diffSegments = segments
            )
        }

        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                if (s1[i - 1] == s2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    val sub = dp[i - 1][j - 1] + 1
                    val insert = dp[i - 1][j] + 1
                    val delete = dp[i][j - 1] + 1
                    dp[i][j] = minOf(sub, insert, delete)
                }
            }
        }

        val segments = mutableListOf<DiffSegment>()
        var i = m
        var j = n

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && s1[i - 1] == s2[j - 1]) {
                segments.add(DiffSegment(char = s1[i - 1], isCorrect = true))
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                // Character mismatch / substitution
                segments.add(DiffSegment(char = s1[i - 1], isCorrect = false))
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                // Extra character typed by user
                segments.add(DiffSegment(char = s1[i - 1], isCorrect = false))
                i--
            } else {
                // Missing character from expected
                segments.add(DiffSegment(char = s2[j - 1], isCorrect = false))
                j--
            }
        }

        segments.reverse()

        return CallsignDiffResult(
            typed = typed,
            expected = expected,
            isMatch = false,
            diffSegments = segments
        )
    }
}
