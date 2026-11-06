package ru.haykor.sudv.shared.domain

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class DepartmentId(val value: Long)

@Serializable
@JvmInline
value class EmployeeId(val value: Long)

@Serializable
@JvmInline
value class UserId(val value: Long)

@Serializable
@JvmInline
value class PenaltyId(val value: Long)

@Serializable
@JvmInline
value class AttachmentId(val value: Long)

@Serializable
@JvmInline
value class ReportId(val value: Long)
