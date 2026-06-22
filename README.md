# DietPhoto

Aplikacja Android do szacowania wartości kalorycznej posiłków na podstawie zdjęć. Klasyfikuje rodzaj jedzenia modelem ViT (Food-101) i wyświetla kalorie oraz makroskładniki dla podanej gramatury.

## Wymagania

- Android 8.0 lub nowszy
- Działający serwer backendowy ([bigos](https://github.com/stolarczykemil/bigos)) dostępny w tej samej sieci

## Konfiguracja przed budowaniem

Otwórz plik `app/src/main/java/com/example/dietphoto/ApiClient.kt` i zmień adres IP na adres maszyny z serwerem:

```kotlin
const val BASE_URL = "http://ADRES_IP_SERWERA/api/"
```

To musi być ten sam adres, który ustawiony jest jako `SERVER_HOST` w pliku `.env` backendu.

## Budowanie APK

W Android Studio: **Build → Generate App Bundles or APKs → Generate APKs**

Gotowy plik: `app/build/outputs/apk/debug/app-debug.apk`

Ewentualnie po prostu `Run`. Z podłączonym urządzeniem lub gotowym emulatorem.

## Struktura projektu

```
app/src/main/java/com/example/dietphoto/
├── ApiClient.kt       # adres serwera i klient HTTP
├── Auth.kt            # JWT, SharedPreferences
├── MainActivity.kt    # nawigacja między ekranami
├── LoginScreen.kt     # logowanie
├── SelectionScreen.kt # wybór trybu (posiłek / etykieta)
├── MealCameraScreen.kt
├── LabelCameraScreen.kt
├── ResultScreen.kt    # wyniki klasyfikacji, kalkulator kalorii
└── FoodData.kt        # tabela kaloryczna 101 kategorii (USDA FoodData Central)
```

## Backend

Repozytorium: [github.com/stolarczykemil/bigos](https://github.com/stolarczykemil/bigos)
