# Consumer rules for data module
# Keep all database entities and DAOs
-keep class com.astralstream.data.database.entities.** { *; }
-keep interface com.astralstream.data.database.dao.** { *; }

# Keep all data models
-keep class com.astralstream.data.models.** { *; }