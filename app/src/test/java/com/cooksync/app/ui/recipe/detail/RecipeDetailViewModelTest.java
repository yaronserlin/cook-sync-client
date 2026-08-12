package com.cooksync.app.ui.recipe.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import com.cooksync.app.data.repository.RecipeRepository;
import com.dtos.response.note.NoteResponse;

import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

/**
 * Unit tests for the pure presentation-logic helpers on {@link RecipeDetailViewModel}:
 * note lookup, star rendering/clamping, and published-date formatting.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class RecipeDetailViewModelTest {

    private RecipeDetailViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new RecipeDetailViewModel(mock(RecipeRepository.class));
    }

    @Test
    public void findRecipeNote_returnsTheNoteWithNoInstructionId() {
        NoteResponse recipeNote = new NoteResponse("n1", "recipe-1", null, "Great recipe");
        NoteResponse stepNote = new NoteResponse("n2", "recipe-1", "step-1", "Do this first");

        NoteResponse found = viewModel.findRecipeNote(List.of(stepNote, recipeNote));

        assertEquals(recipeNote, found);
    }

    @Test
    public void findRecipeNote_returnsNull_whenOnlyStepNotesExist() {
        NoteResponse stepNote = new NoteResponse("n2", "recipe-1", "step-1", "Do this first");

        assertNull(viewModel.findRecipeNote(List.of(stepNote)));
    }

    @Test
    public void findStepNote_returnsTheNoteForThatInstruction() {
        NoteResponse step1Note = new NoteResponse("n1", "recipe-1", "step-1", "First");
        NoteResponse step2Note = new NoteResponse("n2", "recipe-1", "step-2", "Second");

        NoteResponse found = viewModel.findStepNote(List.of(step1Note, step2Note), "step-2");

        assertEquals(step2Note, found);
    }

    @Test
    public void findStepNote_returnsNull_whenNoNoteForThatInstruction() {
        NoteResponse step1Note = new NoteResponse("n1", "recipe-1", "step-1", "First");

        assertNull(viewModel.findStepNote(List.of(step1Note), "step-99"));
    }

    @Test
    public void starsForRating_rendersFilledAndOutlineStars() {
        assertEquals("★★★☆☆", viewModel.starsForRating(3.0));
    }

    @Test
    public void starsForRating_roundsToNearestStar() {
        assertEquals("★★★★☆", viewModel.starsForRating(3.6));
    }

    @Test
    public void starsForRating_rendersAllOutline_whenNull() {
        assertEquals("☆☆☆☆☆", viewModel.starsForRating(null));
    }

    @Test
    public void clampStars_roundsToNearestWholeStar() {
        assertEquals(4, viewModel.clampStars(BigDecimal.valueOf(3.6)));
    }

    @Test
    public void clampStars_clampsBelowRangeToOne() {
        assertEquals(1, viewModel.clampStars(BigDecimal.valueOf(0.2)));
    }

    @Test
    public void clampStars_defaultsToOne_whenNull() {
        assertEquals(1, viewModel.clampStars(null));
    }

    // formatPublishedDate's java.time branch is gated on Build.VERSION.SDK_INT >= O, and the
    // plain JVM unit-test runtime's android.jar stub reports SDK_INT as 0 (no Robolectric),
    // so every case here exercises the pre-Oreo fallback path (a raw 10-character substring)
    // rather than the "Month yyyy" formatting — that branch is only reachable on a real device
    // or under Robolectric.

    @Test
    public void formatPublishedDate_fallsBackToRawDateSubstring_underPlainJvmSdkStub() {
        assertEquals("2026-04-15", viewModel.formatPublishedDate("2026-04-15T10:30:00Z"));
    }

    @Test
    public void formatPublishedDate_returnsEmpty_forNullOrBlank() {
        assertEquals("", viewModel.formatPublishedDate(null));
        assertEquals("", viewModel.formatPublishedDate(""));
    }
}
