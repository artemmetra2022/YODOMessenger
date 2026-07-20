package app.yodo.messenger.di

import app.yodo.messenger.data.repository.AuthRepositoryImpl
import app.yodo.messenger.data.repository.ChatRepositoryImpl
import app.yodo.messenger.data.repository.MessageRepositoryImpl
import app.yodo.messenger.data.repository.NearbyPeopleRepositoryImpl
import app.yodo.messenger.data.repository.PhoneAuthRepositoryImpl
import app.yodo.messenger.data.repository.PresenceRepositoryImpl
import app.yodo.messenger.data.repository.UserRepositoryImpl
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.NearbyPeopleRepository
import app.yodo.messenger.domain.repository.PhoneAuthRepository
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPhoneAuthRepository(impl: PhoneAuthRepositoryImpl): PhoneAuthRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindPresenceRepository(impl: PresenceRepositoryImpl): PresenceRepository

    @Binds
    @Singleton
    abstract fun bindNearbyPeopleRepository(impl: NearbyPeopleRepositoryImpl): NearbyPeopleRepository
}
