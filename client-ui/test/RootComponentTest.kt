package ru.haykor.sudv.client.ui

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.domain.NetworkException
import ru.haykor.sudv.client.ui.login.LoginComponent
import ru.haykor.sudv.client.ui.main.Section
import ru.haykor.sudv.client.ui.splash.SplashComponent
import ru.haykor.sudv.shared.contract.ErrorCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {
    // Components launch on Dispatchers.Main; an unconfined one runs those coroutines right away.
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun root(api: FakeApiClient, deepLink: DeepLink = DeepLink.None) =
        DefaultRootComponent(DefaultComponentContext(LifecycleRegistry()), api, deepLink)

    private val RootComponent.active get() = stack.value.active.instance

    @Test
    fun withoutTokenOpensLogin() {
        val root = root(FakeApiClient(signedIn = false))

        assertIs<RootComponent.Child.Login>(root.active)
    }

    @Test
    fun savedTokenRestoresSession() {
        val root = root(FakeApiClient(signedIn = true))

        val main = assertIs<RootComponent.Child.Main>(root.active)
        assertEquals(TEST_USER, main.component.user)
    }

    @Test
    fun restoreWaitsForServer() {
        val response = CompletableDeferred<Nothing>()
        val api = FakeApiClient(signedIn = true).apply { onMe = { response.await() } }
        val root = root(api)

        val splash = assertIs<RootComponent.Child.Splash>(root.active)
        assertEquals(SplashComponent.State.Loading, splash.component.state.value)
    }

    @Test
    fun unreachableServerOffersRetry() {
        val api = FakeApiClient(signedIn = true).apply { onMe = { throw NetworkException(RuntimeException("refused")) } }
        val root = root(api)

        val splash = assertIs<RootComponent.Child.Splash>(root.active)
        assertEquals(SplashComponent.State.Failed("Нет связи с сервером"), splash.component.state.value)

        api.onMe = { TEST_USER }
        splash.component.onRetry()

        assertIs<RootComponent.Child.Main>(root.active)
    }

    @Test
    fun rejectedSavedTokenOpensLogin() {
        val api = FakeApiClient(signedIn = true)
        api.onMe = {
            // The real client clears the token before throwing.
            api.isSignedIn.value = false
            throw ApiException(ErrorCode.UNAUTHORIZED, "Требуется авторизация")
        }
        val root = root(api)

        assertIs<RootComponent.Child.Login>(root.active)
    }

    @Test
    fun loginOpensMain() {
        val root = root(FakeApiClient(signedIn = false))
        val login = assertIs<RootComponent.Child.Login>(root.active).component

        login.onLoginChange("hr")
        login.onPasswordChange("password123")
        login.onSubmit()

        assertIs<RootComponent.Child.Main>(root.active)
    }

    @Test
    fun wrongPasswordStaysOnLoginWithMessage() {
        val api = FakeApiClient(signedIn = false).apply {
            onLogin = { throw ApiException(ErrorCode.INVALID_CREDENTIALS, "Неверный логин или пароль") }
        }
        val root = root(api)
        val login = assertIs<RootComponent.Child.Login>(root.active).component

        login.onLoginChange("hr")
        login.onPasswordChange("wrong")
        login.onSubmit()

        assertIs<RootComponent.Child.Login>(root.active)
        assertEquals(
            LoginComponent.State(login = "hr", password = "wrong", error = "Неверный логин или пароль"),
            login.state.value,
        )
    }

    @Test
    fun emptyFieldsAreNotSubmitted() {
        var called = false
        val api = FakeApiClient(signedIn = false).apply { onLogin = { called = true; TEST_USER } }
        val login = assertIs<RootComponent.Child.Login>(root(api).active).component

        login.onLoginChange("hr")
        login.onSubmit()

        assertEquals(false, called)
    }

    @Test
    fun logoutOpensLogin() {
        val root = root(FakeApiClient(signedIn = true))
        val main = assertIs<RootComponent.Child.Main>(root.active)

        main.component.onLogout()

        assertIs<RootComponent.Child.Login>(root.active)
    }

    @Test
    fun tokenRejectedLaterOpensLogin() {
        val api = FakeApiClient(signedIn = true)
        val root = root(api)
        assertIs<RootComponent.Child.Main>(root.active)

        api.isSignedIn.value = false

        assertIs<RootComponent.Child.Login>(root.active)
    }

    @Test
    fun deepLinkWaitsForLogin() {
        val root = root(FakeApiClient(signedIn = false), DeepLink.parse("/employees"))
        val login = assertIs<RootComponent.Child.Login>(root.active).component

        login.onLoginChange("hr")
        login.onPasswordChange("password123")
        login.onSubmit()

        val main = assertIs<RootComponent.Child.Main>(root.active).component
        assertEquals(Section.EMPLOYEES, main.stack.value.active.instance.section)
    }

    @Test
    fun deepLinkIsUsedOnlyOnce() {
        val api = FakeApiClient(signedIn = true)
        val root = root(api, DeepLink.parse("/employees"))
        assertIs<RootComponent.Child.Main>(root.active).component.onLogout()
        val login = assertIs<RootComponent.Child.Login>(root.active).component

        login.onLoginChange("hr")
        login.onPasswordChange("password123")
        login.onSubmit()

        val main = assertIs<RootComponent.Child.Main>(root.active).component
        assertEquals(Section.PENALTIES, main.stack.value.active.instance.section)
    }

    @Test
    fun loginScreenHasItsOwnUrl() {
        val root = root(FakeApiClient(signedIn = false))

        assertEquals("login", root.webNavigation.history.value.last().path)
    }

    @Test
    fun mainScreenDelegatesTheUrlToTheSection() {
        val root = root(FakeApiClient(signedIn = true), DeepLink.parse("/employees"))
        val main = assertIs<RootComponent.Child.Main>(root.active).component

        val item = root.webNavigation.history.value.last()
        assertEquals("", item.path)
        assertSame(main, item.child)
    }
}
