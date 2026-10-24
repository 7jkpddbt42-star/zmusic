package com.musicbox.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.ui.theme.ArtworkImage
import com.musicbox.app.ui.theme.THUMB_EDGE_PX

/** 歌单行：封面 + 名称 + 歌曲数 + 右箭头。「发现」与「我的」共用。 */
@Composable
internal fun PlaylistRow(
    summary: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            url = summary.coverUrl,
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(12.dp),
            maxEdgePx = THUMB_EDGE_PX,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = summary.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${summary.trackCount} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = MusicIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}
