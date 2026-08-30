package app.yodo.messenger.di

import app.yodo.messenger.data.repository.AppSettingsRepositoryImpl
import app.yodo.messenger.data.repository.AuthRepositoryImpl
import app.yodo.messenger.data.repository.ChatRepositoryImpl
import app.yodo.messenger.data.repository.MessageRepositoryImpl
import app.yodo.messenger.data.repository.NearbyPeopleRepositoryImpl
import app.yodo.messenger.data.repository.PhoneAuthRepositoryImpl
import app.yodo.messenger.data.repository.PostRepositoryImpl
import app.yodo.messenger.data.repository.PresenceRepositoryImpl
import app.yodo.messenger.data.repository.ReportRepositoryImpl
import app.yodo.messenger.data.repository.SessionRepositoryImpl
import app.yodo.messenger.data.repository.TwoFactorRepositoryImpl
import app.yodo.messenger.data.repository.UserRepositoryImpl
import app.yodo.messenger.domain.repository.AppSettingsRepository
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.NearbyPeopleRepository
import app.yodo.messenger.domain.repository.PhoneAuthRepository
import app.yodo.messenger.domain.repository.PostRepository
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.ReportRepository
import app.yodo.messenger.domain.repository.SessionRepository
import app.yodo.messenger.domain.repository.TwoFactorRepository
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

    @Binds
    @Singleton
    abstract fun bindPostRepository(impl: PostRepositoryImpl): PostRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindTwoFactorRepository(impl: TwoFactorRepositoryImpl): TwoFactorRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: AppSettingsRepositoryImpl): AppSettingsRepository
}
