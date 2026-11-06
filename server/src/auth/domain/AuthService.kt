package ru.haykor.sudv.server.auth.domain

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ru.haykor.sudv.server.core.config.AppConfig
import ru.haykor.sudv.server.core.error.ApiException
import ru.haykor.sudv.server.core.security.CurrentUser
import ru.haykor.sudv.server.records.domain.IEmployeeRepository
import ru.haykor.sudv.shared.contract.CreateUserRequest
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.LoginRequest
import ru.haykor.sudv.shared.contract.LoginResponse
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.UserId
import ru.haykor.sudv.shared.domain.Validation

interface IAuthService {
    suspend fun login(request: LoginRequest): LoginResponse

    /** Called from the JWT `validate` block on every request. */
    suspend fun authenticate(id: UserId): CurrentUser?
    suspend fun currentUser(actor: CurrentUser): UserDto
    suspend fun listUsers(): List<UserDto>
    suspend fun createUser(request: CreateUserRequest): UserDto
    suspend fun setBlocked(actor: CurrentUser, id: UserId, blocked: Boolean): UserDto
}

private const val INVALID_CREDENTIALS_MESSAGE = "Неверный логин или пароль"

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AuthService(
    private val appConfig: AppConfig,
    private val userRepository: IUserRepository,
    private val employeeRepository: IEmployeeRepository,
    private val jwtService: JwtService,
) : IAuthService {

    // Verified for unknown logins too, so response time does not reveal whether a login exists.
    private val dummyHash by lazy { PasswordHasher.hash("dummy-password", appConfig.passwordHashIterations) }

    override suspend fun login(request: LoginRequest): LoginResponse {
        val login = request.login.trim()
        val credentials = userRepository.findCredentials(login)
        val passwordMatches = PasswordHasher.verify(request.password, credentials?.passwordHash ?: dummyHash)
        if (credentials == null || !passwordMatches) {
            throw ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE)
        }
        if (credentials.isBlocked || credentials.employeeArchived) {
            throw ApiException(ErrorCode.USER_BLOCKED, "Учётная запись заблокирована")
        }
        val token = jwtService.issue(credentials.id)
        val dto = userRepository.findById(credentials.id)
            ?: throw ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE)
        return LoginResponse(token, dto)
    }

    override suspend fun authenticate(id: UserId): CurrentUser? {
        val authenticated = userRepository.findAuthenticated(id) ?: return null
        if (authenticated.isBlocked) return null
        return CurrentUser(authenticated.id, authenticated.role, authenticated.departmentId)
    }

    override suspend fun currentUser(actor: CurrentUser): UserDto =
        userRepository.findById(actor.id) ?: throw ApiException(ErrorCode.UNAUTHORIZED, "Требуется авторизация")

    override suspend fun listUsers(): List<UserDto> = userRepository.findAll()

    override suspend fun createUser(request: CreateUserRequest): UserDto {
        val login = request.login.trim()
        val errors = Validation.user(login, request.password)
        if (errors.isNotEmpty()) throw ApiException(ErrorCode.VALIDATION_FAILED, errors.joinToString("; "))

        if (userRepository.existsByLogin(login)) {
            throw ApiException(ErrorCode.DUPLICATE_LOGIN, "Логин уже занят")
        }

        val employee = employeeRepository.findById(request.employeeId)
            ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Сотрудник не найден")
        if (employee.isArchived) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Сотрудник в архиве")
        }
        if (userRepository.existsByEmployee(request.employeeId)) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "У сотрудника уже есть учётная запись")
        }

        val passwordHash = PasswordHasher.hash(request.password, appConfig.passwordHashIterations)
        val id = userRepository.create(login, passwordHash, request.role, request.employeeId)
        return userRepository.findById(id) ?: throw ApiException(ErrorCode.INTERNAL_ERROR, "Не удалось создать пользователя")
    }

    override suspend fun setBlocked(actor: CurrentUser, id: UserId, blocked: Boolean): UserDto {
        userRepository.findById(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Пользователь не найден")
        if (blocked && actor.id == id) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Нельзя блокировать самого себя")
        }
        userRepository.setBlocked(id, blocked)
        return userRepository.findById(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Пользователь не найден")
    }
}
