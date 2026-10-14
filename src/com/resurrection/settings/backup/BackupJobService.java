/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.backup;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** The weekly backup to the folder picked in Remixer. */
public class BackupJobService extends JobService {
    private Thread mThread;

    @Override
    public boolean onStartJob(JobParameters params) {
        mThread = new Thread(() -> {
            RemixBackup.writeWeekly(this);
            jobFinished(params, false);
        }, "RemixBackup");
        mThread.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
