package com.hack.kidhack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")
private val Context.childDataStore: DataStore<Preferences> by preferencesDataStore(name = "selected_child")
private val Context.childOnboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "child_onboarding")

private object Keys {
    val access = stringPreferencesKey("access_token")
    val refresh = stringPreferencesKey("refresh_token")
    val userId = stringPreferencesKey("user_id")
    val loginId = stringPreferencesKey("login_id")
    val name = stringPreferencesKey("name")
    val role = stringPreferencesKey("role")
    val phone = stringPreferencesKey("phone")
    val email = stringPreferencesKey("email")
    val childId = stringPreferencesKey("child_id")
    val childOnboardingDone = booleanPreferencesKey("childOnboardingDone")
}

class SessionRepositoryImpl(
    context: Context,
) : SessionRepository {

    private val dataStore = context.sessionDataStore

    @Volatile
    private var cached: Session? = null

    override val session: Flow<Session?> = dataStore.data.map { it.toSession() }

    init {
        cached = runBlocking { dataStore.data.first().toSession() }
    }

    override suspend fun current(): Session? {
        val value = dataStore.data.first().toSession()
        cached = value
        return value
    }

    override suspend fun save(session: Session) {
        cached = session
        dataStore.edit { prefs ->
            prefs[Keys.access] = session.accessToken
            prefs[Keys.refresh] = session.refreshToken
            prefs[Keys.userId] = session.user.id
            prefs[Keys.loginId] = session.user.loginId
            prefs[Keys.name] = session.user.name
            prefs[Keys.role] = session.user.role.name
            prefs[Keys.phone] = session.user.phone
            if (session.user.email != null) prefs[Keys.email] = session.user.email else prefs.remove(Keys.email)
        }
    }

    override suspend fun clear() {
        cached = null
        dataStore.edit { it.clear() }
    }

    override fun accessToken(): String? = cached?.accessToken

    override fun refreshToken(): String? = cached?.refreshToken

    private fun Preferences.toSession(): Session? {
        val access = this[Keys.access] ?: return null
        val refresh = this[Keys.refresh] ?: return null
        val role = this[Keys.role]?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            user = User(
                id = this[Keys.userId].orEmpty(),
                loginId = this[Keys.loginId].orEmpty(),
                name = this[Keys.name].orEmpty(),
                role = role,
                phone = this[Keys.phone].orEmpty(),
                email = this[Keys.email],
            ),
        )
    }
}

class SelectedChildRepositoryImpl(
    context: Context,
) : SelectedChildRepository {

    private val dataStore = context.childDataStore

    override val selectedChildId: Flow<String?> = dataStore.data.map { it[Keys.childId] }

    override suspend fun current(): String? = dataStore.data.first()[Keys.childId]

    override suspend fun select(childId: String?) {
        dataStore.edit { prefs ->
            if (childId == null) prefs.remove(Keys.childId) else prefs[Keys.childId] = childId
        }
    }
}

class SessionEventsImpl : SessionEvents {
    private val _logout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val logout: Flow<Unit> = _logout.asSharedFlow()
    override fun emitLogout() {
        _logout.tryEmit(Unit)
    }
}

class ChildOnboardingStoreImpl(
    context: Context,
) : ChildOnboardingStore {

    private val dataStore = context.childOnboardingDataStore

    override val done: Flow<Boolean> = dataStore.data.map { it[Keys.childOnboardingDone] ?: false }

    override suspend fun isDone(): Boolean = dataStore.data.first()[Keys.childOnboardingDone] ?: false

    override suspend fun markDone() {
        dataStore.edit { it[Keys.childOnboardingDone] = true }
    }
}
