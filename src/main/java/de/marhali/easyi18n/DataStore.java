package de.marhali.easyi18n;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.*;

import de.marhali.easyi18n.exception.EmptyLocalesDirException;
import de.marhali.easyi18n.io.IOHandler;
import de.marhali.easyi18n.model.TranslationData;
import de.marhali.easyi18n.service.FileChangeListener;
import de.marhali.easyi18n.settings.ProjectSettings;
import de.marhali.easyi18n.settings.ProjectSettingsService;
import de.marhali.easyi18n.util.NotificationHelper;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Responsible for loading, saving and updating translation files.
 * Provides access to the cached translation data which is used in the whole project.
 * @author marhali
 */
public class DataStore {

    private final @NotNull Project project;
    private final @NotNull FileChangeListener changeListener;

    private @NotNull TranslationData data;

    protected DataStore(@NotNull Project project) {
        this.project = project;
        this.data = new TranslationData(true); // Initialize with hard-coded configuration
        this.changeListener = new FileChangeListener(project);

        VirtualFileManager.getInstance().addAsyncFileListener(
                this.changeListener, Disposer.newDisposable(project, "EasyI18n"));
    }

    public @NotNull TranslationData getData() {
        return data;
    }

    /**
     * Loads the translation data into cache and overwrites any previous cached data.
     * If the configuration does not fit an empty translation instance will be populated.
     * @param successResult Consumer will inform if operation was successful
     */
    public void loadFromPersistenceLayer(@NotNull Consumer<Boolean> successResult) {
        ProjectSettings settings = ProjectSettingsService.get(project).getState();

        ApplicationManager.getApplication().saveAll(); // Save opened files (required if new locales were added)

        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                this.data = new IOHandler(settings).read();
                this.changeListener.updateLocalesPath(settings.getLocalesDirectory());
                successResult.accept(true);

            } catch (Exception ex) {
                this.data = new TranslationData(settings.isSorting());
                successResult.accept(false);

                if(ex instanceof EmptyLocalesDirException) {
                    NotificationHelper.createEmptyLocalesDirNotification(project);
                } else {
                    NotificationHelper.createIOError(settings, ex);
                }
            }
        });
    }

    /**
     * Saves the cached translation data to the underlying io system.
     * @param successResult Consumer will inform if operation was successful
     */
    public void saveToPersistenceLayer(@NotNull Consumer<Boolean> successResult) {
        ProjectSettings settings = ProjectSettingsService.get(project).getState();

        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                new IOHandler(settings).write(this.data);
                successResult.accept(true);

            } catch (Exception ex) {
                successResult.accept(false);

                if(ex instanceof EmptyLocalesDirException) {
                    NotificationHelper.createEmptyLocalesDirNotification(project);
                } else {
                    NotificationHelper.createIOError(settings, ex);
                }
            }
        });
    }
}