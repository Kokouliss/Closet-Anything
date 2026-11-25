package com.kokouliss.closetanything.feature.household

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kokouliss.closetanything.core.domain.model.InviteCode
import com.kokouliss.closetanything.core.domain.model.Member
import com.kokouliss.closetanything.core.domain.model.normalizeEmail
import com.kokouliss.closetanything.core.ui.component.AppIcon
import com.kokouliss.closetanything.core.ui.component.AppIcons
import com.kokouliss.closetanything.core.ui.component.InitialAvatar

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MembersSheet(
    state: HouseholdUiState.Ready,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onShareCode: () -> Unit,
    onResetCode: () -> Unit,
    onInviteEmail: (String) -> Unit,
    onCancelEmailInvite: (String) -> Unit,
    onRemoveMember: (memberId: String) -> Unit,
) {
    val household = state.household
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Box {
            Column(
                Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle(stringResource(R.string.feature_household_invite_code_title))
                Hint(stringResource(R.string.feature_household_invite_code_hint))
                SelectionContainer {
                    Text(
                        household.inviteCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                    )
                }
                Hint(stringResource(R.string.feature_household_qr_hint))
                QrCode(InviteCode.link(household.inviteCode), Modifier.size(200.dp).align(Alignment.CenterHorizontally))
                // Wraps, so both buttons fit at a large font.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onShareCode) {
                        AppIcon(AppIcons.Share, null, Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.feature_household_share_code))
                    }
                    // Only the owner may replace the code (enforced by the security rules as well).
                    if (state.isOwner) {
                        TextButton(onClick = onResetCode) { Text(stringResource(R.string.feature_household_reset_code)) }
                    }
                }

                HorizontalDivider()
                SectionTitle(stringResource(R.string.feature_household_invite_by_email_title))
                EmailInviteField(onInviteEmail)
                household.invitedEmails.forEach { email ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(AppIcons.Mail, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            email,
                            Modifier.weight(1f).padding(horizontal = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onCancelEmailInvite(email) }) {
                            AppIcon(AppIcons.Close, stringResource(R.string.feature_household_cancel_invite, email))
                        }
                    }
                }

                HorizontalDivider()
                SectionTitle(stringResource(R.string.feature_household_members_title))
                household.members.forEach { member ->
                    MemberRow(
                        member = member,
                        isYou = member.id == state.currentUserId,
                        isHouseholdOwner = member.id == household.ownerId,
                        onRemove = if (state.isOwner && member.id != household.ownerId) {
                            { onRemoveMember(member.id) }
                        } else {
                            null
                        },
                    )
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Black on white whatever the theme, because that is what cameras read best. */
@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    // Size 0 asks for the smallest code: one cell per module plus the quiet border.
    val matrix = remember(content) { QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0) }
    val description = stringResource(R.string.feature_household_qr_description)
    Canvas(modifier.background(Color.White).semantics { contentDescription = description }) {
        val cell = size.minDimension / matrix.width
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                // Half a pixel wider, so rounding leaves no hairlines between dark cells.
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

@Composable
private fun EmailInviteField(onInviteEmail: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var invited by rememberSaveable { mutableStateOf<String?>(null) }
    // Set when Send is tried with a bad address: the field then explains itself until the address is fixed.
    var showError by rememberSaveable { mutableStateOf(false) }
    val isValid = normalizeEmail(email) != null
    val send = {
        if (isValid) {
            onInviteEmail(email)
            invited = normalizeEmail(email)
            email = ""
        }
        showError = !isValid
    }
    OutlinedTextField(
        value = email,
        onValueChange = { email = it.take(254) },
        label = { Text(stringResource(R.string.feature_household_email_label)) },
        isError = showError && !isValid,
        supportingText = {
            val hint = stringResource(R.string.feature_household_email_hint)
            Text(
                when {
                    showError && !isValid -> stringResource(R.string.feature_household_email_invalid)
                    invited != null -> stringResource(R.string.feature_household_invited, invited.orEmpty()) + " " + hint
                    else -> hint
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = send, enabled = isValid) {
                AppIcon(AppIcons.Send, stringResource(R.string.feature_household_send_invite))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MemberRow(member: Member, isYou: Boolean, isHouseholdOwner: Boolean, onRemove: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InitialAvatar(member.name)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                if (isYou) stringResource(R.string.feature_household_you, member.name) else member.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isHouseholdOwner) Hint(stringResource(R.string.feature_household_owner))
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                AppIcon(AppIcons.Close, stringResource(R.string.feature_household_remove_member, member.name))
            }
        }
    }
}
