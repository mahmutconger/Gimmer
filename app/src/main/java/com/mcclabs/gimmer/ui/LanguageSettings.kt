package com.mcclabs.gimmer.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.Language
import com.mcclabs.gimmer.LanguagePreferences
import com.mcclabs.gimmer.MainActivity
import com.mcclabs.gimmer.R

@Composable
internal fun LanguageSettings(viewModel: WorkoutViewModel) {
    val activity = checkNotNull(LocalActivity.current as? MainActivity)
    val exerciseLanguage by viewModel.exerciseLanguage.collectAsStateWithLifecycle()
    SettingsCard(stringResource(R.string.languages), stringResource(R.string.languages_description)) {
        LanguageOptions(
            stringResource(R.string.app_language),
            LanguagePreferences(activity).appLanguage,
            activity::setAppLanguage,
        )
        LanguageOptions(
            stringResource(R.string.exercise_language),
            exerciseLanguage,
            viewModel::setExerciseLanguage,
        )
    }
}

@Composable
private fun LanguageOptions(label: String, selected: Language, onSelect: (Language) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Language.entries.forEach { language ->
            PillChip(language.label, selected == language, onClick = { onSelect(language) })
        }
    }
}
