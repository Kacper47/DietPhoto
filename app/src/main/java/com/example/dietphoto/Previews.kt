// Podglądy composables w Android Studio (tylko do developmentu).
package com.example.dietphoto

import android.net.Uri
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.example.dietphoto.ui.theme.DietPhotoTheme

//@Preview(showBackground = true)
//@Composable
//fun LoginScreenPreview() {
//    DietPhotoTheme {
//        LoginScreen(onLoginSuccess = { _, _ -> })
//    }S
//}
//
//@Preview(showBackground = true)
//@Composable
//fun PhotoActionDialogPreview() {
//    DietPhotoTheme {
//        PhotoActionDialog(
//            photoUri = Uri.EMPTY,
//            onDismiss = {},
//            onUpload = {}
//        )
//    }
//}

@Preview(showBackground = true)
@Composable
fun SelectionScreenPreview() {
    DietPhotoTheme {
        SelectionScreen(
            onMealSelected = {},
            onLabelSelected = {},
            onLogout = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ResultScreenPreview() {
    DietPhotoTheme {
        ResultScreen(
            photos = emptyList(),
            isMealMode = true,
            onBack = {}
        )
    }
}