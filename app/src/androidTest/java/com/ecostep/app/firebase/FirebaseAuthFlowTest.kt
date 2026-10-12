package com.ecostep.app.firebase

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.core.theme.EcoStepTheme
import com.ecostep.app.data.firebase.FirebaseAuthRepository
import com.ecostep.app.data.firebase.FirestoreProfileRepository
import com.ecostep.app.ui.adapters.RepositoryAuthDataSource
import com.ecostep.app.ui.screens.LoginScreen
import com.ecostep.app.ui.viewmodels.LoginViewModel
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt in with firebaseEmulatorHost, or firebaseLiveTest=true for a disposable live Auth account.
 * Live tests do not create Firestore documents (client rules forbid deleting profiles).
 */
class FirebaseAuthFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun registrationScreenAndFirebaseRoundTrip() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("firebaseEmulatorHost")
        val live = args.getString("firebaseLiveTest") == "true"
        assumeTrue("Explicit Firebase test target required", live || !host.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = if (live) FirebaseApp.getInstance().options else FirebaseOptions.Builder()
            .setProjectId("demo-ecostep").setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey("demo-api-key").build()
        val app = FirebaseApp.initializeApp(context, options, "auth-flow-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app)
        if (!live) auth.useEmulator(host!!, 9099)
        val repository = FirebaseAuthRepository(auth)
        val email = "ecostep-auth-${UUID.randomUUID()}@example.com"
        val password = "EcoStep-test-123!"
        val completed = AtomicBoolean(false)
        lateinit var vm: LoginViewModel
        val active = mutableStateOf<LoginViewModel?>(null)
        try {
            compose.runOnIdle {
                vm = LoginViewModel(RepositoryAuthDataSource(repository))
                active.value = vm
            }
            compose.setContent { EcoStepTheme { LoginScreen(active.value!!) { completed.set(true) } } }
            compose.onNodeWithText("Create an account").performScrollTo().performClick()
            compose.onNodeWithTag("auth-name").performScrollTo().performTextInput("Firebase Test")
            compose.onNodeWithTag("auth-email").performScrollTo().performTextInput(email)
            compose.onNodeWithTag("auth-password").performScrollTo().performTextInput(password)
            compose.onNodeWithTag("auth-confirm-password").performScrollTo().performTextInput("wrong")
            compose.onNodeWithText("Create account").performScrollTo().performClick()
            compose.waitUntil(5_000) { vm.uiState.value.errorMessage == "Passwords do not match." }
            assertNull(repository.currentUserId)
            compose.runOnIdle { vm.updateConfirmPassword(password) }
            compose.onNodeWithText("Create account").performScrollTo().performClick()
            compose.waitUntil(60_000) { completed.get() || vm.uiState.value.errorMessage != null }
            assertTrue(vm.uiState.value.errorMessage, completed.get())
            val uid = repository.currentUserId
            assertNotNull(uid)
            assertEquals("Firebase Test", repository.currentUserDisplayName)
            assertEquals("", vm.uiState.value.password)
            assertNotNull(repository.getIdToken())

            expectFailure("already exists") { repository.signUp(email, password, "Duplicate") }
            assertEquals(uid, repository.currentUserId)
            repository.signOut()
            assertNull(repository.currentUserId)
            expectFailure("incorrect") { repository.signIn(email, "incorrect-password") }
            assertNull(repository.currentUserId)
            repository.signIn(email, password)
            assertEquals(uid, repository.currentUserId)
            assertEquals("Firebase Test", repository.currentUserDisplayName)

            repository.signOut()
            compose.runOnIdle {
                completed.set(false)
                vm = LoginViewModel(RepositoryAuthDataSource(repository))
                active.value = vm
            }
            compose.onNodeWithTag("auth-email").performScrollTo().performTextInput(email)
            compose.onNodeWithTag("auth-password").performScrollTo().performTextInput(password)
            compose.onNodeWithText("Sign in").performScrollTo().performClick()
            compose.waitUntil(60_000) { completed.get() || vm.uiState.value.errorMessage != null }
            assertTrue(vm.uiState.value.errorMessage, completed.get())
            assertEquals(uid, repository.currentUserId)

            if (!live) {
                val db = FirebaseFirestore.getInstance(app).apply { useEmulator(host!!, 8080) }
                val profiles = FirestoreProfileRepository(repository, db)
                assertTrue(profiles.ensureProfile(repository.currentUserDisplayName!!))
                assertFalse(profiles.ensureProfile("Should not overwrite"))
                assertEquals("Firebase Test", profiles.getProfile()?.displayName)
                compose.runOnIdle {
                    completed.set(false)
                    vm = LoginViewModel(RepositoryAuthDataSource(repository))
                    active.value = vm
                }
                compose.onNodeWithText("Forgot password?").performScrollTo().performClick()
                compose.onNodeWithTag("auth-email").performScrollTo().performTextInput(email)
                compose.onNodeWithText("Send reset email").performScrollTo().performClick()
                compose.waitUntil(30_000) {
                    vm.uiState.value.confirmationMessage != null || vm.uiState.value.errorMessage != null
                }
                assertNull(vm.uiState.value.errorMessage)
                assertNotNull(vm.uiState.value.confirmationMessage)
                assertFalse(completed.get())
                val payload = URL("http://$host:9099/emulator/v1/projects/demo-ecostep/oobCodes").readText()
                val codes = JSONObject(payload).getJSONArray("oobCodes")
                val code = (0 until codes.length()).map { codes.getJSONObject(it) }
                    .last { it.getString("email") == email }.getString("oobCode")
                val newPassword = "New-EcoStep-test-123!"
                Tasks.await(auth.confirmPasswordReset(code, newPassword), 15, TimeUnit.SECONDS)
                repository.signOut()
                expectFailure("incorrect") { repository.signIn(email, password) }
                repository.signIn(email, newPassword)
                assertEquals(uid, repository.currentUserId)
            }
        } finally {
            // Delete only the account created by this test, even if a preceding assertion failed.
            if (auth.currentUser?.email != email) {
                runCatching { repository.signIn(email, password) }
            }
            if (auth.currentUser?.email == email) {
                Tasks.await(auth.currentUser!!.delete(), 20, TimeUnit.SECONDS)
            }
            auth.signOut()
            // Auth SDK caches app-scoped services for the instrumentation process.
            // Keep the named app alive so subsequent Firebase test classes remain usable.
        }
    }

    private suspend fun expectFailure(message: String, request: suspend () -> Unit) {
        val error = try {
            withTimeout(30_000) { request() }
            null
        } catch (exception: IllegalStateException) { exception }
        assertNotNull("Expected Firebase rejection", error)
        assertTrue(error!!.message, error.message.orEmpty().contains(message))
    }
}
