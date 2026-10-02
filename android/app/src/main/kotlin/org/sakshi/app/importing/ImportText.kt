package org.sakshi.app.importing

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.sakshi.acquisition.importer.ImportFailure
import org.sakshi.acquisition.importer.ImportLimits
import org.sakshi.acquisition.importer.Rejection
import org.sakshi.app.R

@Composable
fun rejectionText(reason: Rejection): String = when (reason) {
    Rejection.UNSUPPORTED_SCHEME -> stringResource(R.string.rejection_unsupported_scheme)
    Rejection.UNREADABLE -> stringResource(R.string.rejection_unreadable)
    Rejection.TOO_MANY_ITEMS -> pluralStringResource(R.plurals.rejection_too_many, ImportLimits().maxItems, ImportLimits().maxItems)
    Rejection.EMPTY_TEXT -> stringResource(R.string.rejection_empty_text)
    Rejection.TEXT_TOO_LONG -> stringResource(R.string.rejection_text_too_long)
    Rejection.NOT_A_URI -> stringResource(R.string.rejection_not_a_uri)
}

@Composable
fun failureText(reason: ImportFailure): String = when (reason) {
    ImportFailure.CASE_UNAVAILABLE -> stringResource(R.string.failure_case_unavailable)
    ImportFailure.TOO_LARGE -> stringResource(R.string.failure_too_large)
    ImportFailure.UNREADABLE -> stringResource(R.string.failure_unreadable)
    ImportFailure.ACCESS_DENIED -> stringResource(R.string.failure_access_denied)
    ImportFailure.STORAGE_ERROR -> stringResource(R.string.failure_storage_error)
    ImportFailure.KEY_UNAVAILABLE -> stringResource(R.string.failure_key_unavailable)
    ImportFailure.CANCELLED -> stringResource(R.string.failure_cancelled)
}
