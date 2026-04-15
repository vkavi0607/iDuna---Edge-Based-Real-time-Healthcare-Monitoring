package com.iduna.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iduna.domain.model.UserProfile
import com.iduna.ui.components.IdunaCard
import com.iduna.ui.components.ScreenBackground
import com.iduna.ui.components.SectionTitle

@Composable
fun ProfileScreen(
    profile: UserProfile,
    onSave: (String, String, String) -> Unit,
) {
    val name = remember { mutableStateOf(profile.name) }
    val age = remember { mutableStateOf(profile.age.toString()) }
    val emergencyContact = remember { mutableStateOf(profile.emergencyContact) }

    LaunchedEffect(profile) {
        name.value = profile.name
        age.value = profile.age.toString()
        emergencyContact.value = profile.emergencyContact
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IdunaCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle(
                    title = "Personal Profile",
                    subtitle = "Stored locally for emergency escalation and PDF reports",
                )
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = age.value,
                    onValueChange = { age.value = it },
                    label = { Text("Age") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = emergencyContact.value,
                    onValueChange = { emergencyContact.value = it },
                    label = { Text("Emergency Contact Number") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onSave(name.value, age.value, emergencyContact.value) }) {
                    Text("Save Profile")
                }
            }
        }
    }
}
