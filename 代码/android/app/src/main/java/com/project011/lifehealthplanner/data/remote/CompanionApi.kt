package com.project011.lifehealthplanner.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface CompanionApi {
    @GET("status")
    suspend fun status(): StatusDto

    @POST("pair/complete")
    suspend fun pair(@Body request: PairCompleteRequestDto): PairCompleteResponseDto

    @POST("ai/plan")
    suspend fun createPlan(@Body request: PlanRequestDto): PlanDraftDto

    @POST("ai/chat")
    suspend fun chat(@Body request: ChatRequestDto): ChatReplyDto

    @PUT("backups/{deviceId}")
    suspend fun uploadBackup(
        @Path("deviceId") deviceId: String,
        @Body request: BackupEnvelopeDto,
    ): BackupReceiptDto

    @GET("backups/{deviceId}/latest")
    suspend fun latestBackup(@Path("deviceId") deviceId: String): BackupEnvelopeDto

    @DELETE("pair/{deviceId}")
    suspend fun revokePairing(@Path("deviceId") deviceId: String): Map<String, Boolean>
}
