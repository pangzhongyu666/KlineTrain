package com.klinetrain.app.ui.formula

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.db.FormulaEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FormulaViewModel : ViewModel() {

    private val dao = KlineTrainApp.instance.database.formulaDao()

    /** 全部公式列表 */
    val formulas: StateFlow<List<FormulaEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    /** 保存(新建或更新, dao 为 REPLACE 策略)，完成后回调 */
    fun save(entity: FormulaEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            dao.insert(entity)
            onDone()
        }
    }

    fun delete(entity: FormulaEntity) {
        viewModelScope.launch { dao.delete(entity) }
    }
}
