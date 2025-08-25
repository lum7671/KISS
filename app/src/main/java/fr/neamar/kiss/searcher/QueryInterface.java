package fr.neamar.kiss.searcher;

import fr.neamar.kiss.ui.ListPopup;

public interface QueryInterface {
    void temporarilyDisableTranscriptMode();
    void updateTranscriptMode(int transcriptMode);

    void launchOccurred();

    void registerPopup(ListPopup popup);

    @SuppressWarnings("deprecation")
    void showDialog(android.app.DialogFragment dialog);
}
