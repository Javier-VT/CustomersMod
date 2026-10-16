# Customers (NeoForge 1.21.1)

Port del addon de Bedrock "Customers Add-on" v0.6.3 a NeoForge 1.21.1 (el mismo NeoForge 21.1.262 de tu modpack).

## Cómo obtener el .jar (sin instalar nada)
1. Crea un repositorio nuevo en GitHub y sube TODO el contenido de esta carpeta (incluida `.github`).
2. Ve a la pestaña **Actions** → "Build mod" (se ejecuta solo al subir; también hay botón *Run workflow*).
3. Cuando termine (~3-5 min), abre la ejecución y descarga el artefacto **customers-mod**: dentro está `customers-0.6.3.jar`.
4. Copia el .jar a la carpeta `mods` de tu instancia en MojoLauncher.

Si prefieres compilar en tu PC: `./gradlew build` (necesita Java 21); el .jar queda en `build/libs/`.

## Qué incluye
- 11 sillas (roble, abeto, abedul, jungla, acacia, roble oscuro, manglar, cerezo, bambú, carmesí, distorsionado), cartel de restaurante y campana para llevar, con las mismas recetas.
- Cliente NPC (huevo de aparición) con 11 skins, perfiles (normal/apurado/paciente/generoso/VIP), pedidos, propinas, reputación, horario de día y pedidos para llevar.
- Menú automático: toma comida de vanilla y de cualquier mod (Farmer's Delight, Let's Do, etc.).
- Las pantallas de Bedrock (formularios) son ahora pantallas del juego: clic derecho al cartel o a la campana.
- Idiomas: inglés y español (México).

## Diferencias respecto a Bedrock
- No hay silla de roble pálido (no existe en 1.21.1).
- Los modelos con inclinación de 18° usan 22.5° (límite de Java).
- Los datos del restaurante se guardan por mundo.
