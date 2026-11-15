package net.maui.morselab.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.maui.morselab.R
import net.maui.morselab.databinding.FragmentQsoTrainerBinding
import net.maui.morselab.trainer.OptionHighlightState
import net.maui.morselab.trainer.QsoCategory
import net.maui.morselab.trainer.QsoTrainerUiMapper
import net.maui.morselab.trainer.QsoTrainerUiState
import net.maui.morselab.viewmodel.QsoTrainerViewModel
import javax.inject.Inject

@AndroidEntryPoint
class QsoTrainerFragment @Inject constructor() : Fragment() {

    private val viewModel: QsoTrainerViewModel by activityViewModels()
    private var _binding: FragmentQsoTrainerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQsoTrainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val optionButtons = listOf(
            binding.buttonOption1,
            binding.buttonOption2,
            binding.buttonOption3,
            binding.buttonOption4
        )

        binding.radioGroupCategory.setOnCheckedChangeListener { _, checkedId ->
            val selectedCategory = when (checkedId) {
                R.id.radioSingleCodes -> QsoCategory.SINGLE_CODES
                R.id.radioFullPhrases -> QsoCategory.FULL_PHRASES
                R.id.radioMixed -> QsoCategory.MIXED
                else -> return@setOnCheckedChangeListener
            }
            if (viewModel.uiState.value.selectedCategory != selectedCategory) {
                viewModel.selectCategory(selectedCategory)
            }
        }

        binding.buttonReplayAudio.setOnClickListener {
            viewModel.replayAudio()
        }

        optionButtons.forEach { button ->
            button.setOnClickListener {
                button.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                viewModel.selectOption(button.text.toString())
            }
        }

        binding.buttonNext.setOnClickListener {
            viewModel.nextQuestion()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state, optionButtons)
                }
            }
        }
    }

    private fun renderUiState(state: QsoTrainerUiState, optionButtons: List<Button>) {
        val targetRadioId = when (state.selectedCategory) {
            QsoCategory.SINGLE_CODES -> R.id.radioSingleCodes
            QsoCategory.FULL_PHRASES -> R.id.radioFullPhrases
            QsoCategory.MIXED -> R.id.radioMixed
        }
        if (binding.radioGroupCategory.checkedRadioButtonId != targetRadioId) {
            binding.radioGroupCategory.check(targetRadioId)
        }

        val uiModel = QsoTrainerUiMapper.map(state)

        binding.textViewScore.text = uiModel.scoreText
        binding.textViewStreak.text = uiModel.streakText
        binding.textViewRank.text = uiModel.rankText
        binding.textViewXp.text = uiModel.xpText
        binding.progressBarRank.progress = uiModel.rankProgressPercent
        binding.textViewDailyStreak.text = uiModel.dailyStreakText
        binding.textViewMastery.text = uiModel.masteryText
        binding.textViewFeedback.text = uiModel.feedbackText
        binding.textViewMorsePattern.text = uiModel.morsePatternText
        binding.textViewMeaning.text = uiModel.meaningText
        binding.buttonNext.isEnabled = uiModel.isNextEnabled

        optionButtons.forEachIndexed { index, button ->
            val optionState = uiModel.optionStates.getOrNull(index)
            if (optionState != null) {
                button.visibility = View.VISIBLE
                button.text = optionState.text
                button.isEnabled = uiModel.areOptionsEnabled
                button.backgroundTintList = when (optionState.highlightState) {
                    OptionHighlightState.DEFAULT -> null
                    OptionHighlightState.CORRECT -> ColorStateList.valueOf(COLOR_CORRECT)
                    OptionHighlightState.INCORRECT -> ColorStateList.valueOf(COLOR_INCORRECT)
                }
            } else {
                button.visibility = View.GONE
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopAudio()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val COLOR_CORRECT = Color.parseColor("#2E7D32")
        private val COLOR_INCORRECT = Color.parseColor("#C62828")
    }
}
