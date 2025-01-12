/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package com.openmrs.android_sdk.library.api.workers.provider;

import java.io.IOException;

import retrofit2.Response;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.openmrs.android_sdk.R;
import com.openmrs.android_sdk.library.OpenmrsAndroid;
import com.openmrs.android_sdk.library.api.RestApi;
import com.openmrs.android_sdk.library.api.RestServiceBuilder;
import com.openmrs.android_sdk.library.dao.ProviderRoomDAO;
import com.openmrs.android_sdk.library.databases.AppDatabase;
import com.openmrs.android_sdk.library.models.Provider;
import com.openmrs.android_sdk.utilities.NetworkUtils;
import com.openmrs.android_sdk.utilities.ToastUtil;

/**
 * The type Update provider worker.
 */
public class UpdateProviderWorker extends Worker {
    ProviderRoomDAO providerRoomDao;
    RestApi restApi;

    /**
     * Instantiates a new Update provider worker.
     *
     * @param context      the context
     * @param workerParams the worker params
     */
    public UpdateProviderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        restApi = RestServiceBuilder.createService(RestApi.class);
        providerRoomDao = AppDatabase.getDatabase(getApplicationContext()).providerRoomDAO();
    }

    @NonNull
    @Override
    public Result doWork() {
        String providerUuidTobeUpdated = getInputData().getString("uuid");
        Provider provider = providerRoomDao.findProviderByUUID(providerUuidTobeUpdated).blockingGet();

        if (provider == null) {
            return Result.failure();
        } else {
            provider.getPerson().setUuid(null);

            if (updateProvider(restApi, provider)) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    ToastUtil.success(OpenmrsAndroid.getInstance().getString(R.string.edit_provider_success_msg));
                    OpenmrsAndroid.getOpenMRSLogger().e(OpenmrsAndroid.getInstance().getString(R.string.edit_provider_success_msg));
                });
                return Result.success();
            } else {
                return Result.retry();
            }
        }
    }

    private boolean updateProvider(RestApi restApi, Provider provider) {
        if (NetworkUtils.isOnline()) {
            try {
                Response<Provider> response = restApi.updateProvider(provider.getUuid(), provider).execute();
                if (response.isSuccessful()) {
                    providerRoomDao.updateProviderByUuid(response.body().getDisplay(), provider.getId(), response.body().getPerson(), response.body().getUuid(),
                            response.body().getIdentifier());
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
