package ru.haykor.sudv.client.ui.users

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.load
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.UserId

interface UserListComponent {
    val state: Value<State>

    fun onBlockClick(user: UserDto)
    fun onBlockConfirm()
    fun onBlockDismiss()
    fun onUnblockClick(user: UserDto)
    fun onCreateClick()
    fun onRefresh()

    /** Called by the section after an account was created. */
    fun reload()

    data class State(
        val users: LoadState<List<UserDto>> = LoadState.Loading,
        /** The signed-in admin: the server refuses to block yourself, so the UI does not offer it. */
        val currentUserId: UserId,
        val confirmingBlock: UserDto? = null,
        val busyUserId: UserId? = null,
        val actionError: String? = null,
    )
}

class DefaultUserListComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    currentUserId: UserId,
    private val onCreate: () -> Unit,
) : UserListComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue(UserListComponent.State(currentUserId = currentUserId))
    override val state: Value<UserListComponent.State> = _state

    init {
        reload()
    }

    override fun onBlockClick(user: UserDto) = _state.update { it.copy(confirmingBlock = user, actionError = null) }

    override fun onBlockDismiss() = _state.update { it.copy(confirmingBlock = null) }

    override fun onBlockConfirm() {
        val user = _state.value.confirmingBlock ?: return
        _state.update { it.copy(confirmingBlock = null) }
        setBlocked(user, blocked = true)
    }

    // Unblocking only gives access back, so it needs no confirmation.
    override fun onUnblockClick(user: UserDto) = setBlocked(user, blocked = false)

    override fun onCreateClick() = onCreate()

    override fun onRefresh() = reload()

    override fun reload() {
        scope.launch {
            if (_state.value.users !is LoadState.Loaded) _state.update { it.copy(users = LoadState.Loading) }
            val result = load { apiClient.users() }
            _state.update { it.copy(users = result) }
        }
    }

    private fun setBlocked(user: UserDto, blocked: Boolean) {
        _state.update { it.copy(busyUserId = user.id, actionError = null) }
        scope.launch {
            try {
                val updated = if (blocked) apiClient.blockUser(user.id) else apiClient.unblockUser(user.id)
                _state.update { state ->
                    val users = (state.users as? LoadState.Loaded)?.data?.map { if (it.id == updated.id) updated else it }
                    state.copy(users = users?.let { LoadState.Loaded(it) } ?: state.users, busyUserId = null)
                }
            } catch (e: ApiClientException) {
                _state.update { it.copy(busyUserId = null, actionError = e.message) }
            }
        }
    }
}
