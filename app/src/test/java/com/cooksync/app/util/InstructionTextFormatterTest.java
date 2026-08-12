package com.cooksync.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link InstructionTextFormatter}. Covers sentence splitting/parenthetical
 * detection, ingredient-mention matching, and step-text sizing — the plain-Java logic
 * {@code CookingModeActivity} turns into {@code Spannable} styling.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class InstructionTextFormatterTest {

    // ── splitIntoSentences ────────────────────────────────────────────

    @Test
    public void splitIntoSentences_empty_forNullOrBlank() {
        assertTrue(InstructionTextFormatter.splitIntoSentences(null).isEmpty());
        assertTrue(InstructionTextFormatter.splitIntoSentences("   ").isEmpty());
    }

    @Test
    public void splitIntoSentences_splitsOnSentenceBoundaries() {
        List<InstructionTextFormatter.Sentence> sentences =
                InstructionTextFormatter.splitIntoSentences("Chop the onions. Heat the oil in a pan.");

        assertEquals(2, sentences.size());
        assertEquals("Chop the onions.", sentences.get(0).text());
        assertEquals("Heat the oil in a pan.", sentences.get(1).text());
    }

    @Test
    public void splitIntoSentences_doesNotSplitOnDecimalsOrAbbreviations() {
        List<InstructionTextFormatter.Sentence> sentences =
                InstructionTextFormatter.splitIntoSentences("Add 1.5 cups of flour and 1 tbsp. sugar.");

        assertEquals(1, sentences.size());
    }

    @Test
    public void splitIntoSentences_flagsParentheticalSentence() {
        List<InstructionTextFormatter.Sentence> sentences =
                InstructionTextFormatter.splitIntoSentences("Stir well. (Substitute butter for oil if needed.)");

        assertFalse(sentences.get(0).parenthetical());
        assertTrue(sentences.get(1).parenthetical());
    }

    // ── findIngredientMentions ────────────────────────────────────────

    @Test
    public void findIngredientMentions_empty_whenTextOrNamesNull() {
        assertTrue(InstructionTextFormatter.findIngredientMentions(null, List.of("Salt")).isEmpty());
        assertTrue(InstructionTextFormatter.findIngredientMentions("Add salt", null).isEmpty());
    }

    @Test
    public void findIngredientMentions_matchesWholeWord_caseInsensitive() {
        List<InstructionTextFormatter.Mention> mentions =
                InstructionTextFormatter.findIngredientMentions("Add the Tomato and stir.", List.of("tomato"));

        assertEquals(1, mentions.size());
        InstructionTextFormatter.Mention mention = mentions.get(0);
        assertEquals("Tomato", "Add the Tomato and stir.".substring(mention.start(), mention.end()));
    }

    @Test
    public void findIngredientMentions_skipsNameNotPresentInText() {
        assertTrue(InstructionTextFormatter.findIngredientMentions("Add the tomato.", List.of("Basil")).isEmpty());
    }

    @Test
    public void findIngredientMentions_doesNotMatchPartialWord() {
        // "egg" should not match inside "eggplant".
        assertTrue(InstructionTextFormatter.findIngredientMentions("Slice the eggplant.", List.of("egg")).isEmpty());
    }

    @Test
    public void findIngredientMentions_skipsBlankOrNullNames() {
        assertTrue(InstructionTextFormatter.findIngredientMentions("Add salt.", java.util.Arrays.asList(null, "  ")).isEmpty());
    }

    // ── stepTextSizeSp ────────────────────────────────────────────────

    @Test
    public void stepTextSizeSp_largestSize_forShortOrNullDescription() {
        assertEquals(34f, InstructionTextFormatter.stepTextSizeSp(null), 0f);
        assertEquals(34f, InstructionTextFormatter.stepTextSizeSp("Stir well."), 0f);
    }

    @Test
    public void stepTextSizeSp_shrinksAsDescriptionGrowsLonger() {
        String len80 = "x".repeat(80);
        String len150 = "x".repeat(150);
        String len400 = "x".repeat(400);

        assertEquals(29f, InstructionTextFormatter.stepTextSizeSp(len80), 0f);
        assertEquals(25f, InstructionTextFormatter.stepTextSizeSp(len150), 0f);
        assertEquals(19f, InstructionTextFormatter.stepTextSizeSp(len400), 0f);
    }
}
