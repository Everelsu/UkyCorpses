# Выпуск версии

Один тег — один релиз: workflow `.github/workflows/release.yml` собирает мод из помеченного коммита и выкладывает
jar в релиз на GitHub, а если настроены — ещё на Modrinth и CurseForge. Версия, файл и текст изменений берутся из
репозитория, вбивать их руками на сайтах не нужно.

## Откуда берётся версия

Нигде в файлах её нет: сборка считает её из git.

- Коммит с тегом `1.0.0` и без незакоммиченных изменений — версия `1.0.0`, файл `build/libs/ukycorpses-1.0.0.jar`.
- После тега были коммиты — что-то вроде `1.0.0-main.3+28ce0c1`.
- Есть незакоммиченные изменения — в конце `-dirty`.
- Тегов нет вовсе — версия из хэша коммита.

Тег — без буквы `v`, только `1.2.3` (или `1.2.3-pre1` для беты). Другой формат сборка не пропустит
(`versionPattern` в `gradle.properties`).

## Каждый релиз

1. **Напиши изменения.** Раздел `## 1.2.3` сверху `CHANGELOG.md`. Именно его покажут GitHub, Modrinth и
   CurseForge; без него релиз не соберётся.
2. **Закоммить всё** — `git status` должен быть пустым.
3. **Поставь тег и отправь его:**

```bash
git tag 1.2.3
git push origin 1.2.3
```

Дальше всё делает GitHub Actions (вкладка Actions → Release). Готовый релиз появится во вкладке Releases с
файлом `ukycorpses-1.2.3.jar`. Тег вида `1.2.3-pre1` выходит как бета (pre-release).

Собрать то же самое у себя, без публикации:

```bash
./gradlew build
```

Нужен файл `build/libs/ukycorpses-<версия>.jar`. Файлы `-dev.jar` и `-sources.jar` — для разработки, их не выкладывай.

## Modrinth и CurseForge (по желанию)

Магазин используется, только если для него есть **оба**: ID проекта и токен. Нет одного из них — шаг пропускается,
релиз на GitHub всё равно выходит.

**1. Создай проекты руками** (через токен их не создать):

- Modrinth — <https://modrinth.com/dashboard/projects>: Forge, Minecraft 1.7.10, клиент и сервер.
- CurseForge — <https://legacy.curseforge.com/project/create>: Minecraft → Mods.

**2. Впиши ID проектов в `gradle.properties`** (они публичные, их можно коммитить):

```properties
modrinthProjectId = ukycorpses
curseForgeProjectId = 1234567
```

Modrinth принимает слаг или ID. CurseForge — только числовой Project ID из блока About Project.

**3. Добавь токены в секреты репозитория** — Settings → Secrets and variables → Actions → New repository secret:

| Секрет             | Где взять                                                       |
| ------------------ | --------------------------------------------------------------- |
| `MODRINTH_TOKEN`   | <https://modrinth.com/settings/pats>, право *Create versions*    |
| `CURSEFORGE_TOKEN` | <https://legacy.curseforge.com/account/api-tokens>              |

Токены никогда не кладутся в файлы репозитория.

## Выпуск по частям

Workflow можно запустить и руками: Actions → Release → Run workflow. В «Use workflow from» выбери **тег**, и отметь,
куда выкладывать: GitHub, Modrinth, CurseForge. Ничего не отмечено — только сборка, jar остаётся в артефактах
запуска: так можно прогнать всё вхолостую.

Это же способ доделать релиз, который выложился не везде. **Загрузку в магазин не отменить**, повторная создаст
дубликат, поэтому перезапускай, отметив только то место, где не получилось.
