package dev.mauicoder.morsekata

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.mauicoder.morsekata.data.UserPreferencesRepository
import dev.mauicoder.morsekata.databinding.ActivityMainBinding
import dev.mauicoder.morsekata.fragment.ConfigFragment
import dev.mauicoder.morsekata.fragment.MorseDecoderFragment
import dev.mauicoder.morsekata.fragment.PlayTextFragment
import dev.mauicoder.morsekata.fragment.QsoTrainerFragment
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    @Inject
    lateinit var configFragment: ConfigFragment
    @Inject
    lateinit var playTextFragment: PlayTextFragment

    @Inject
    lateinit var decoderFragment: MorseDecoderFragment

    @Inject
    lateinit var qsoTrainerFragment: QsoTrainerFragment

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private fun replaceFragment(fragment: Fragment) : Boolean {
        supportFragmentManager.beginTransaction().replace(R.id.flFragment, fragment).commit()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigationView.setOnItemSelectedListener {
            when(it.itemId) {
                R.id.miPlay -> replaceFragment(playTextFragment)
                R.id.miDecode -> {
                    if (userPreferencesRepository.userPreferences.value.showExperimentalFeatures) {
                        replaceFragment(decoderFragment)
                    } else {
                        false
                    }
                }
                R.id.miTrain -> replaceFragment(qsoTrainerFragment)
                R.id.miConfig -> replaceFragment(configFragment)
                else -> false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferencesRepository.userPreferences.collect { preferences ->
                    binding.bottomNavigationView.menu.findItem(R.id.miDecode)?.isVisible =
                        preferences.showExperimentalFeatures

                    if (!preferences.showExperimentalFeatures &&
                        binding.bottomNavigationView.selectedItemId == R.id.miDecode
                    ) {
                        binding.bottomNavigationView.selectedItemId = R.id.miPlay
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            replaceFragment(playTextFragment) // place the home-fragment
        }
    }

}
