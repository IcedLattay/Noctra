package com.noctra.app.ui.companion

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.transition.AutoTransition
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.noctra.app.R
import com.noctra.app.databinding.FragmentCompanionBinding
import com.noctra.app.ui.companion.adapters.ShopItemAdapter
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CompanionFragment : Fragment() {

    private var _binding: FragmentCompanionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CompanionViewModel by activityViewModels()
    private lateinit var shopAdapter: ShopItemAdapter

    private var currentStageLevel: Int = -1
    private var previousOutfitAsset: String? = null
    private var isCustomizeMode = false
    
    // Dialog Queue Logic
    private sealed class PendingDialog {
        data class MorningRecap(val score: Int, val xp: Int) : PendingDialog()
        data class StreakNotice(val type: CompanionViewModel.CompanionNotice) : PendingDialog()
        data class Evolution(val stageName: String) : PendingDialog()
    }

    private val dialogQueue = mutableListOf<PendingDialog>()
    private var isDialogShowing = false

    private fun enqueueDialog(dialog: PendingDialog) {
        dialogQueue.add(dialog)
        // Sort by Priority: Recap(0) > Notice(1) > Evolution(2)
        dialogQueue.sortWith(compareBy { 
            when(it) {
                is PendingDialog.MorningRecap -> 0
                is PendingDialog.StreakNotice -> 1
                is PendingDialog.Evolution -> 2
            }
        })
        processNextDialog()
    }

    private fun processNextDialog() {
        if (isDialogShowing || dialogQueue.isEmpty()) return
        
        isDialogShowing = true
        val next = dialogQueue.removeAt(0)
        
        when (next) {
            is PendingDialog.MorningRecap -> {
                MorningSleepPopupDialog.newInstance(next.score, next.xp).apply {
                    setOnDismissCallback { onDialogClosed() }
                    show(childFragmentManager, "MorningSleepPopup")
                }
            }
            is PendingDialog.StreakNotice -> {
                StreakNoticeDialogFragment.newInstance(next.type).apply {
                    setOnDismissCallback { onDialogClosed() }
                    show(childFragmentManager, "StreakNoticePopup")
                }
            }
            is PendingDialog.Evolution -> {
                EvolutionDialogFragment.newInstance(next.stageName).apply {
                    setOnDismissCallback { onDialogClosed() }
                    show(childFragmentManager, "EvolutionPopup")
                }
            }
        }
    }

    private fun onDialogClosed() {
        isDialogShowing = false
        // Delay slightly to avoid window focus flickers
        view?.postDelayed({ processNextDialog() }, 300)
    }

    private val shleepyStates = mapOf(
        1 to ShleepyState("DEPRIVED"),
        2 to ShleepyState("AWAKENING"),
        3 to ShleepyState("CHARGED"),
        4 to ShleepyState("OVERDRIVE"),
        5 to ShleepyState("ZEN")
    )

    data class ShleepyState(val name: String)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        setupBackNavigation()
        observeUiState()

        // Start skeleton animations: spinner spins natively, placeholders pulse
        binding.skeletonSpinner.visibility = View.VISIBLE
        val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse_skeleton)
        binding.skeletonTokenPill.startAnimation(pulseAnim)
        binding.skeletonStageLabel.startAnimation(pulseAnim)
        binding.skeletonXpCard.startAnimation(pulseAnim)
        binding.skeletonCustomize.startAnimation(pulseAnim)

        val userId = UserSession.getUserId(requireContext()) ?: return
        val lastShownSleepDate = requireContext().getSharedPreferences("noctra_prefs", Context.MODE_PRIVATE)
            .getString("last_shown_sleep_date", null)
            
        viewModel.loadData(userId, lastShownSleepDate)
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isCustomizeMode) {
                    toggleCustomizeMode(false)
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        shopAdapter = ShopItemAdapter { model ->
            handleItemClick(model)
        }
        binding.rvShopItems.adapter = shopAdapter
    }

    private fun handleItemClick(model: CompanionViewModel.ShopItemUiModel) {
        val userId = UserSession.getUserId(requireContext()) ?: return
        if (model.isOwned) {
            viewModel.equipItem(userId, model.item)
        } else if (model.canAfford) {
            showPurchaseConfirmation(model.item)
        } else {
            android.widget.Toast.makeText(requireContext(), "Insufficient Dream Tokens", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPurchaseConfirmation(item: com.noctra.app.data.model.ShopItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Purchase Item")
            .setMessage("Buy ${item.label} for ${item.tokenCost} Dream Tokens?")
            .setPositiveButton("Purchase") { _, _ ->
                val userId = UserSession.getUserId(requireContext()) ?: return@setPositiveButton
                viewModel.purchaseAndEquip(userId, item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAllItems() {
        val allItems = viewModel.uiState.value.shopItems
        shopAdapter.submitList(allItems)

        if (allItems.isEmpty()) {
            binding.rvShopItems.visibility = View.GONE
            binding.tvComingSoon.visibility = View.VISIBLE
        } else {
            binding.rvShopItems.visibility = View.VISIBLE
            binding.tvComingSoon.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val userId = UserSession.getUserId(requireContext()) ?: return
        val lastShownSleepDate = requireContext().getSharedPreferences("noctra_prefs", Context.MODE_PRIVATE)
            .getString("last_shown_sleep_date", null)
        viewModel.loadData(userId, lastShownSleepDate)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUi(state)
                    }
                }
                launch {
                    viewModel.showMorningPopup.collectLatest { (score, xp) ->
                        // Mark this session date's recap as shown. Keyed to the
                        // SESSION date (last night), not today — records are
                        // written twice (provisional + finalization), so the
                        // flag, not record existence, prevents re-showing.
                        val sessionDate = java.time.LocalDate.now().minusDays(1).toString()
                        requireContext().getSharedPreferences("noctra_prefs", Context.MODE_PRIVATE)
                            .edit(commit = false) { putString("last_shown_sleep_date", sessionDate) }

                        enqueueDialog(PendingDialog.MorningRecap(score, xp))
                    }
                }
                launch {
                    viewModel.showNoticePopup.collectLatest { notice ->
                        enqueueDialog(PendingDialog.StreakNotice(notice))
                    }
                }
                launch {
                    viewModel.showEvolutionPopup.collectLatest { evolution ->
                        enqueueDialog(PendingDialog.Evolution(evolution.stageName))
                    }
                }
            }
        }
    }

    private fun updateUi(state: CompanionViewModel.CompanionUiState) {
        val noInternetView = view?.findViewById<View>(R.id.noInternetView)
        val companionRoot = view?.findViewById<View>(R.id.companionRoot)

        if (state.isOffline) {
            noInternetView?.visibility = View.VISIBLE
            companionRoot?.visibility = View.GONE
            setupRetryButton()
            return
        } else {
            noInternetView?.visibility = View.GONE
            companionRoot?.visibility = View.VISIBLE
        }

        with(binding) {
            tvTokenBalance.text = state.tokenBalance.toString()
            // Only swap token skeleton once data has actually loaded
            if (state.evolutionState != null) {
                tokenContainer.visibility = View.VISIBLE
                skeletonTokenPill.clearAnimation()
                skeletonTokenPill.visibility = View.GONE
            } else {
                tokenContainer.visibility = View.GONE
                skeletonTokenPill.visibility = View.VISIBLE
            }

            state.evolutionState?.let { evolution ->
                tvStageLabel.text = evolution.stageName
                tvXpValue.text = getString(R.string.companion_xp_unit, evolution.totalXp)
                pbXpProgress.progress = (evolution.progressPercent * 100).toInt()

                // Show data components and hide their skeletons
                tvStageLabel.visibility = View.VISIBLE
                cvXpCard.visibility = View.VISIBLE
                btnCustomize.visibility = View.VISIBLE
                skeletonStageLabel.clearAnimation()
                skeletonXpCard.clearAnimation()
                skeletonCustomize.clearAnimation()
                skeletonStageLabel.visibility = View.GONE
                skeletonXpCard.visibility = View.GONE
                skeletonCustomize.visibility = View.GONE

                // 1. Update Shleepy Animation if stage changed
                if (currentStageLevel != evolution.stageLevel) {
                    currentStageLevel = evolution.stageLevel
                    applyIdleAnimation(playSmoke = false)
                }

                // 2. Update outfit animation if equipped outfit changed
                val currentOutfitAsset = viewModel.uiState.value.equippedOutfit?.itemAsset
                val outfitChanged = previousOutfitAsset != null && previousOutfitAsset != currentOutfitAsset
                applyIdleAnimation(playSmoke = outfitChanged)
                previousOutfitAsset = currentOutfitAsset
                
                // 3. Update Shop Items if in customize mode
                if (isCustomizeMode) {
                    showAllItems()
                }
            }
            
            if (state.error != null) {
                android.widget.Toast.makeText(requireContext(), state.error, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRetryButton() {
        val noInternetView = view?.findViewById<View>(R.id.noInternetView) ?: return
        val btnRetry = noInternetView.findViewById<android.widget.ImageButton>(R.id.btnRetry)
        val progressRetry = noInternetView.findViewById<android.widget.ProgressBar>(R.id.progressRetry)
        val tvRetry = noInternetView.findViewById<TextView>(R.id.tvRetry)

        btnRetry.setOnClickListener {
            btnRetry.visibility = View.GONE
            progressRetry.visibility = View.VISIBLE
            tvRetry.text = "retrying..."

            val userId = UserSession.getUserId(requireContext()) ?: return@setOnClickListener
            viewModel.retry(userId)
        }
    }

    private fun applyIdleAnimation(playSmoke: Boolean = false) {
        val state = shleepyStates[currentStageLevel] ?: shleepyStates[1]!!
        val stageName = state.name.lowercase()
        val outfitAsset = viewModel.uiState.value.equippedOutfit?.itemAsset ?: "default"

        val resId = resources.getIdentifier(
            "shleepy_${stageName}_${outfitAsset}", "raw", requireContext().packageName
        )

        if (resId != 0) {
            binding.petAnimationView.apply {
                setAnimation(resId)
                repeatCount = -1
                playAnimation()
            }
            // Hide skeleton spinner once pet animation is ready
            binding.skeletonSpinner.visibility = View.GONE
        } else {
            binding.petAnimationView.cancelAnimation()
            binding.petAnimationView.progress = 0f
        }

        if (playSmoke) {
            val smokeResId = resources.getIdentifier("smoke", "raw", requireContext().packageName)
            if (smokeResId != 0) {
                binding.smokeOverlay.setAnimation(smokeResId)
                binding.smokeOverlay.playAnimation()
            }
        }
    }

    private fun setupListeners() {
        binding.btnCustomize.setOnClickListener {
            toggleCustomizeMode(true)
        }

        binding.btnBack.setOnClickListener {
            toggleCustomizeMode(false)
        }
    }

    private fun toggleCustomizeMode(enabled: Boolean) {
        isCustomizeMode = enabled
        
        val activityRoot = requireActivity().findViewById<ViewGroup>(R.id.mainActivityRoot) ?: return
        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_nav)

        // Single coordinated transition for EVERYTHING (Activity root + Fragment contents)
        val consolidatedTransition = TransitionSet().apply {
            // Handle Bottom Nav: Slide + Fade to prevent "white bar"
            addTransition(Slide(Gravity.BOTTOM).addTarget(bottomNav))
            addTransition(Fade().addTarget(bottomNav))
            
            // Handle Fragment internal UI: Panels
            addTransition(Slide(Gravity.BOTTOM).addTarget(binding.shopPanel))
            addTransition(Fade(Fade.IN).addTarget(binding.shopPanel))
            addTransition(Fade(Fade.OUT).addTarget(binding.shopPanel))
            addTransition(Fade(Fade.OUT).addTarget(binding.companionPanel))
            addTransition(Fade(Fade.IN).addTarget(binding.companionPanel))
            
            // Smoothly animate the expansion/movement of everything else
            addTransition(ChangeBounds())
            
            ordering = TransitionSet.ORDERING_TOGETHER
            duration = 450 // Slightly longer to let the curve feel smooth
            interpolator = FastOutSlowInInterpolator()
        }
        
        TransitionManager.beginDelayedTransition(activityRoot, consolidatedTransition)
        
        // Update Bottom Nav Visibility
        bottomNav?.visibility = if (enabled) View.GONE else View.VISIBLE

        if (enabled) {
            // Hide companion elements
            binding.companionPanel.visibility = View.GONE
            
            // Show shop elements
            binding.shopPanel.visibility = View.VISIBLE
            binding.btnBack.visibility = View.VISIBLE
            
            // Re-constrain shleepyFrame bottom to shopPanel (companionPanel is GONE)
            val shleepyParams = binding.shleepyFrame.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            shleepyParams.bottomToTop = R.id.shopPanel
            shleepyParams.topMargin = (16 * resources.displayMetrics.density).toInt()
            binding.shleepyFrame.layoutParams = shleepyParams
            
            // Extend floor upward
            val floorParams = binding.companionFloor.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            floorParams.height = (510 * resources.displayMetrics.density).toInt()
            binding.companionFloor.layoutParams = floorParams
            
            showAllItems()
        } else {
            // Show companion elements
            binding.companionPanel.visibility = View.VISIBLE
            
            // Hide shop elements
            binding.shopPanel.visibility = View.GONE
            binding.btnBack.visibility = View.GONE
            
            // Restore shleepyFrame bottom to companionPanel
            val shleepyParams = binding.shleepyFrame.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            shleepyParams.bottomToTop = R.id.companionPanel
            shleepyParams.topMargin = (100 * resources.displayMetrics.density).toInt()
            binding.shleepyFrame.layoutParams = shleepyParams
            
            // Shrink floor back
            val floorParams = binding.companionFloor.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            floorParams.height = (340 * resources.displayMetrics.density).toInt()
            binding.companionFloor.layoutParams = floorParams
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure Bottom Nav is restored if we leave while in customize mode
        if (isCustomizeMode) {
            requireActivity().findViewById<View>(R.id.bottom_nav)?.visibility = View.VISIBLE
        }
        currentStageLevel = -1
        previousOutfitAsset = null
        _binding = null
    }
}
