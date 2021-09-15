package io.github.leffinger.crossyourheart;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.github.leffinger.crossyourheart.room.Database;

@RunWith(AndroidJUnit4.class)
public class AutoMigrationTest {
    @Rule
    public MigrationTestHelper helper;

    @Before
    public void setUp() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                                         Database.class);
    }

    @Test
    public void migrateAll() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(Database.DB_NAME, 1);
        db.close();

        Database database = Database.getInstance(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        database.getOpenHelper().getWritableDatabase();
        database.close();
    }
}
