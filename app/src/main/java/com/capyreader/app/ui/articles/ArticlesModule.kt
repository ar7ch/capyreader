package com.capyreader.app.ui.articles

import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.ui.addintent.AddLinkViewModel
import com.capyreader.app.ui.articles.audio.AudioPlayerController
import com.capyreader.app.ui.articles.feeds.edit.EditFeedViewModel
import com.jocmp.capy.ai.SummaryQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.capyreader.app.ui.articles.reader.VideoAspectRatios
import com.capyreader.app.ui.articles.reader.VideoPlayerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

internal val articlesModule = module {
    single { ArticleSessionCutoff() }
    factory {
        AddFeedViewModel(
            account = get(),
        )
    }
    factory {
        AddLinkViewModel(
            account = get(),
        )
    }
    single {
        VideoAspectRatios(
            context = get()
        )
    }
    single {
        AudioPlayerController(
            context = get()
        )
    }
    single {
        val appPreferences = get<AppPreferences>()

        SummaryQueue(
            account = get(),
            summarizer = get(),
            settings = { appPreferences.aiOptions.settings() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
    viewModel {
        val appPreferences = get<AppPreferences>()

        ArticleScreenViewModel(
            account = get(),
            appPreferences = appPreferences,
            notificationHelper = get(),
            application = get(),
            articleCutoff = get(),
        )
    }
    viewModel {
        ArticleViewModel(
            account = get(),
            appPreferences = get(),
            application = get(),
            notificationHelper = get(),
            articleCutoff = get(),
            summaryQueue = get(),
        )
    }
    viewModel {
        VideoPlayerViewModel(
            application = get(),
        )
    }
    viewModel {
        EditFeedViewModel(
            account = get(),
        )
    }
    viewModel {
        EditFolderViewModel(
            account = get(),
            appPreferences = get(),
        )
    }
}
