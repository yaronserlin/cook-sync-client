package com.cooksync.app.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pure text-analysis logic backing Cooking Mode's instruction-step display: sentence splitting,
 * parenthetical-aside detection, ingredient-mention matching, and step-text sizing. Deliberately
 * framework-free (no {@code android.text}/{@code android.graphics} types) so it's independently
 * unit-testable; {@code CookingModeActivity} turns this class's plain results into the actual
 * {@code Spannable} styling, which does need the Android framework and a {@code Context} for
 * color resolution.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public final class InstructionTextFormatter {

    /** Matches a sentence boundary: punctuation immediately followed by whitespace and a capital
     *  letter or an opening parenthesis. Deliberately does not match after decimals ("1.5 cups")
     *  or abbreviations ("1 tbsp. sugar"), since the character following the space there is
     *  lowercase or a digit, not the capital/paren this pattern requires. */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z(])");

    private InstructionTextFormatter() {
    }

    /** One sentence of a split instruction, flagged if it reads as a parenthetical aside. */
    public record Sentence(String text, boolean parenthetical) {
    }

    /** One [start, end) character range within the joined instruction text where an ingredient
     *  name was found. */
    public record Mention(int start, int end) {
    }

    /**
     * Splits a step's description into trimmed, non-empty sentences on sentence boundaries,
     * flagging any that read as a parenthetical aside (e.g. an ingredient substitution note) so
     * the caller can visually mute it.
     *
     * Complexity:
     * Time: O(n) where n is the description's length
     * Space: O(n)
     *
     * @param description the step's raw instruction text, may be {@code null}/blank
     * @return the split sentences, in order; empty if {@code description} is {@code null}/blank
     */
    public static List<Sentence> splitIntoSentences(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        List<Sentence> sentences = new ArrayList<>();
        for (String raw : SENTENCE_BOUNDARY.split(description.trim())) {
            String sentence = raw.trim();
            if (!sentence.isEmpty()) {
                sentences.add(new Sentence(sentence, sentence.startsWith("(")));
            }
        }
        return sentences;
    }

    /**
     * Finds every whole-word, case-insensitive occurrence of any of {@code ingredientNames}
     * within {@code text}, for bolding ingredient mentions in the running instruction prose. An
     * ingredient name that doesn't literally appear in the text (or whose name is itself an
     * unmatchable pattern) is simply skipped — this is a readability aid, not a requirement that
     * every ingredient be found.
     *
     * Complexity:
     * Time: O(n * m) where n is the text length and m is the number of ingredient names
     * Space: O(k) where k is the number of matches found
     *
     * @param text the already sentence-joined instruction text to search
     * @param ingredientNames this step's ingredient names, may contain blanks
     * @return every match's character range, in the order found
     */
    public static List<Mention> findIngredientMentions(String text, Collection<String> ingredientNames) {
        List<Mention> mentions = new ArrayList<>();
        if (text == null || ingredientNames == null) {
            return mentions;
        }
        for (String rawName : ingredientNames) {
            String name = rawName == null ? null : rawName.trim();
            if (name == null || name.isEmpty()) continue;
            Matcher matcher;
            try {
                matcher = Pattern.compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text);
            } catch (PatternSyntaxException e) {
                continue;
            }
            while (matcher.find()) {
                mentions.add(new Mention(matcher.start(), matcher.end()));
            }
        }
        return mentions;
    }

    /**
     * Picks the step-text font size that keeps a short instruction large and prominent while
     * letting a long one still fit comfortably, rather than a single fixed size that's either
     * too small for a one-liner or overflowing for a paragraph.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param description the step's instruction text, may be {@code null}
     * @return the text size in SP, ready to pass to {@code TextView#setTextSize}
     */
    public static float stepTextSizeSp(String description) {
        int length = description == null ? 0 : description.length();
        if (length <= 60) return 34f;
        if (length <= 120) return 29f;
        if (length <= 220) return 25f;
        if (length <= 350) return 22f;
        return 19f;
    }
}
