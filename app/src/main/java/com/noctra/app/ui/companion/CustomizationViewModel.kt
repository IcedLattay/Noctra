package com.noctra.app.ui.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.ShopItem
import com.noctra.app.data.repository.InventoryRepository
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.ShopRepository
import com.noctra.app.domain.usecase.CompanionEvolutionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

class CustomizationViewModel(
    private val shopRepository: ShopRepository = ShopRepository(),
    private val inventoryRepository: InventoryRepository = InventoryRepository(),
    private val rewardRepository: RewardLedgerRepository = RewardLedgerRepository(),
    private val evolutionUseCase: CompanionEvolutionUseCase = CompanionEvolutionUseCase()
) : ViewModel() {

    data class ShopItemUiModel(
        val item: ShopItem,
        val isOwned: Boolean,
        val isEquipped: Boolean,
        val canAfford: Boolean
    )

    data class CustomizationUiState(
        val isLoading: Boolean = false,
        val tokenBalance: Int = 0,
        val stageLevel: Int = 1,
        val items: List<ShopItemUiModel> = emptyList(),
        val equippedItems: Map<String, ShopItem> = emptyMap(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(CustomizationUiState())
    val uiState: StateFlow<CustomizationUiState> = _uiState.asStateFlow()

    fun purchaseAndEquip(userId: String, item: ShopItem) {
        viewModelScope.launch {
            try {
                val ledger = rewardRepository.getRewardLedger(userId)
                if (ledger != null && ledger.tokenBalance >= item.tokenCost) {
                    val newBalance = ledger.tokenBalance - item.tokenCost
                    
                    // 1. Optimistic UI update for tokens AND item state
                    _uiState.update { currentState ->
                        val updatedItems = currentState.items.map { model ->
                            if (model.item.itemId == item.itemId) {
                                model.copy(isOwned = true, isEquipped = true)
                            } else if (model.item.category == item.category) {
                                model.copy(isEquipped = false)
                            } else {
                                model
                            }
                        }
                        currentState.copy(
                            tokenBalance = newBalance,
                            items = updatedItems,
                            equippedItems = currentState.equippedItems.toMutableMap().apply {
                                put(item.category, item)
                            }
                        )
                    }

                    // 2. Background DB updates
                    val updatedLedger = ledger.copy(
                        tokenBalance = newBalance,
                        lastUpdated = OffsetDateTime.now().toString()
                    )
                    rewardRepository.updateRewardLedger(updatedLedger)
                    inventoryRepository.purchaseItem(userId, item.itemId)
                    performEquip(userId, item)
                    
                    // 3. Final background refresh to ensure sync
                    loadDataInternal(userId, showLoading = false)
                } else {
                    _uiState.update { it.copy(error = "Insufficient tokens") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Purchase failed: ${e.message}") }
                // On error, reload to fix UI
                loadData(userId)
            }
        }
    }

    fun equipItem(userId: String, item: ShopItem) {
        val isCurrentlyEquipped = _uiState.value.items.find { it.item.itemId == item.itemId }?.isEquipped ?: false
        
        viewModelScope.launch {
            try {
                // Optimistic UI update (Toggle)
                _uiState.update { currentState ->
                    val updatedItems = currentState.items.map { model ->
                        if (model.item.itemId == item.itemId) {
                            model.copy(isEquipped = !isCurrentlyEquipped)
                        } else if (model.item.category == item.category) {
                            model.copy(isEquipped = false)
                        } else {
                            model
                        }
                    }
                    
                    val newEquippedMap = currentState.equippedItems.toMutableMap()
                    if (isCurrentlyEquipped) {
                        newEquippedMap.remove(item.category)
                    } else {
                        newEquippedMap[item.category] = item
                    }

                    currentState.copy(
                        items = updatedItems,
                        equippedItems = newEquippedMap
                    )
                }
                
                if (isCurrentlyEquipped) {
                    inventoryRepository.unequipItem(userId, item.itemId)
                } else {
                    performEquip(userId, item)
                }
                loadDataInternal(userId, showLoading = false)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Equip failed: ${e.message}") }
                loadData(userId)
            }
        }
    }

    private suspend fun performEquip(userId: String, item: ShopItem) {
        val allItems = shopRepository.getAllShopItems()
        val itemIdsInCategory = allItems.filter { it.category == item.category }.map { it.itemId }
        inventoryRepository.equipItem(userId, item.itemId, itemIdsInCategory)
    }

    fun loadData(userId: String) {
        viewModelScope.launch {
            loadDataInternal(userId, showLoading = true)
        }
    }

    private suspend fun loadDataInternal(userId: String, showLoading: Boolean) {
        if (showLoading) _uiState.update { it.copy(isLoading = true) }
        try {
            val allItems = shopRepository.getAllShopItems()
            val inventory = inventoryRepository.getUserInventory(userId)
            val ledger = rewardRepository.getRewardLedger(userId)
            val balance = ledger?.tokenBalance ?: 0

            val inventoryItemIds = inventory.map { it.itemId }.toSet()
            val equippedItemIds = inventory.filter { it.isEquipped }.map { it.itemId }.toSet()

            val stageLevel = ledger?.let { evolutionUseCase.execute(it.totalXp).stageLevel } ?: 1

            val uiModels = allItems.map { item ->
                ShopItemUiModel(
                    item = item,
                    isOwned = inventoryItemIds.contains(item.itemId),
                    isEquipped = equippedItemIds.contains(item.itemId),
                    canAfford = balance >= item.tokenCost
                )
            }

            val equippedMap = allItems.filter { equippedItemIds.contains(it.itemId) }
                .associateBy { it.category }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    tokenBalance = balance,
                    stageLevel = stageLevel,
                    items = uiModels,
                    equippedItems = equippedMap,
                    error = null
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }
}
