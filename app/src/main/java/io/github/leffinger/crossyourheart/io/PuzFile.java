package io.github.leffinger.crossyourheart.io;

import android.util.Log;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * Parses and updates Across Lite (puz) files.
 *
 * <p>Many thanks to the authors of this explainer for the file format:
 * https://code.google.com/archive/p/puz/wikis/FileFormat.wiki
 */
public class PuzFile extends AbstractPuzzleFile {
    public static final byte GEXT_MASK_CIRCLED = (byte) 0x80;
    public static final byte GEXT_MASK_NONE = 0x0;
    private static final String TAG = "PuzFile";
    private static final String MAGIC = "ACROSS&DOWN";
    private static final String GRBS_SECTION_NAME = "GRBS";
    private static final String RTBL_SECTION_NAME = "RTBL";
    private static final String RUSR_SECTION_NAME = "RUSR";
    private static final String LTIM_SECTION_NAME = "LTIM";
    private static final String GEXT_SECTION_NAME = "GEXT";
    final int mFileChecksum;
    final byte[] mMagic;
    final int mHeaderChecksum;
    final byte[] mMaskedChecksums;
    final byte[] mVersionString;
    final boolean mIncludeNoteInTextChecksum;
    final int mUnknownBitmask;
    final int mScrambledChecksum;
    final byte mWidth;
    final byte mHeight;
    final int mNumClues;
    final int mScrambledTag;
    final byte[] mSolution;
    final byte[] mGrid;
    final byte[] mTitle;
    final byte[] mAuthor;
    final byte[] mCopyright;
    final Clue[] mClues;
    final byte[] mNote;
    final List<Section> mExtraSections;
    final int[] mAcrossClueMapping;
    final int[] mDownClueMapping;
    final byte[][] mUserRebusEntries;
    final boolean[][] mClueReferences;
    TimerInfo mTimerInfo;

    public PuzFile(int fileChecksum, byte[] magic, int headerChecksum, byte[] maskedChecksums,
                   byte[] versionString, boolean includeNoteInTextChecksum, int scrambledChecksum,
                   byte width, byte height, int numClues, int unknownBitmask, int scrambledTag,
                   byte[] solution, byte[] grid, byte[] title, byte[] author, byte[] copyright,
                   Clue[] clues, byte[] note, List<Section> extraSections, int[] acrossClueMapping,
                   int[] downClueMapping, byte[][] userRebusEntries, boolean[][] clueReferences,
                   TimerInfo timerInfo) {
        mFileChecksum = fileChecksum;
        mMagic = magic;
        mHeaderChecksum = headerChecksum;
        mMaskedChecksums = maskedChecksums;
        mVersionString = versionString;
        mIncludeNoteInTextChecksum = includeNoteInTextChecksum;
        mScrambledChecksum = scrambledChecksum;
        mWidth = width;
        mHeight = height;
        mNumClues = numClues;
        mUnknownBitmask = unknownBitmask;
        mScrambledTag = scrambledTag;
        mSolution = solution;
        mGrid = grid;
        mTitle = title;
        mAuthor = author;
        mCopyright = copyright;
        mNote = note;
        mExtraSections = extraSections;
        mClues = clues;
        mAcrossClueMapping = acrossClueMapping;
        mDownClueMapping = downClueMapping;
        mUserRebusEntries = userRebusEntries;
        mClueReferences = clueReferences;
        mTimerInfo = timerInfo;
    }

    /**
     * Loads a puzzle file with minimal correctness checks. Useful when a file is known to be the
     * correct format.
     *
     * @param inputStream puzzle file data
     * @return parsed puz file
     * @throws IOException if loading fails
     */
    public static PuzFile loadPuzFile(InputStream inputStream) throws IOException {
        LittleEndianDataInputStream dataInputStream = new LittleEndianDataInputStream(inputStream);

        // Get header info.
        final int fileChecksum = dataInputStream.readUnsignedShort();
        final byte[] magic = readNullTerminatedByteString(dataInputStream);

        // Error out early if the magic string is wrong (not a puz file at all).
        String magicString = new String(magic, ISO_8859_1);
        if (!magicString.equals(MAGIC)) {
            throw new IOException("Wrong file magic; is this a puz file?");
        }

        final int headerChecksum = dataInputStream.readUnsignedShort();
        final byte[] maskedChecksums = new byte[8];
        dataInputStream.read(maskedChecksums);
        final byte[] versionStringBytes = new byte[4];
        dataInputStream.read(versionStringBytes);

        final boolean includeNoteInTextChecksum = includeNoteInTextChecksum(versionStringBytes);
        dataInputStream.skipBytes(2);  // skip junk
        final int scrambledChecksum = dataInputStream.readUnsignedShort();
        dataInputStream.skipBytes(12);  // skip junk

        // Get basic puzzle info: width, height, etc.
        final byte width = dataInputStream.readByte();
        final byte height = dataInputStream.readByte();
        final int numClues = dataInputStream.readUnsignedShort();
        final int unknownBitmask = dataInputStream.readUnsignedShort();
        final int scrambledTag = dataInputStream.readUnsignedShort();

        // Read the solution grid and puzzle state.
        int puzzleSize = width * height;
        final byte[] solution = new byte[puzzleSize];
        if (dataInputStream.read(solution) < puzzleSize) {
            throw new EOFException();
        }
        final byte[] puzzleState = new byte[puzzleSize];
        if (dataInputStream.read(puzzleState) < puzzleSize) {
            throw new EOFException();
        }

        // Read variable-length strings: title, author, clues, etc.
        final byte[] title = readNullTerminatedByteString(dataInputStream);
        final byte[] author = readNullTerminatedByteString(dataInputStream);
        final byte[] copyright = readNullTerminatedByteString(dataInputStream);
        final byte[][] clueTexts = new byte[numClues][];
        for (int i = 0; i < numClues; i++) {
            clueTexts[i] = readNullTerminatedByteString(dataInputStream);
        }
        final byte[] note = readNullTerminatedByteString(dataInputStream);

        // Read extra sections. Each of these start with a four-byte key with the name of the
        // section, and include a length and checksum.
        List<Section> extraSections = new ArrayList<>();
        while (true) {
            Section section = Section.readSection(dataInputStream);
            if (section == null) {
                break;
            }
            extraSections.add(section);
        }

        // Clue assignment. This part is not persisted, but we do it here so that (1) we don't
        // have to redo this math whenever we create a ViewModel and (2) we can fail loading if
        // there is an issue assigning clues.
        Clue[] clues = new Clue[numClues];
        for (int i = 0; i < numClues; i++) {
            clues[i] = new Clue(new String(clueTexts[i], ISO_8859_1));
        }
        int[] acrossClueMapping = new int[height * width];
        int[] downClueMapping = new int[height * width];

        // Split cells into groups of across clues, iterating in row-major order. The values here
        // are lists of offsets into the grid/solution arrays.
        List<List<Integer>> acrossClues = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            List<Integer> nextClue = null;
            for (int col = 0; col < width; col++) {
                int offset = row * width + col;
                if (solution[offset] == '.') {
                    if (nextClue != null && nextClue.size() > 2) {
                        acrossClues.add(nextClue);
                    }
                    nextClue = null;
                } else {
                    if (nextClue == null) {
                        nextClue = new ArrayList<>();
                    }
                    nextClue.add(offset);
                }
            }
            if (nextClue != null && nextClue.size() > 2) {
                acrossClues.add(nextClue);
            }
        }

        // Split cells into groups of down clues, iterating in column-major order.
        List<List<Integer>> downClues = new ArrayList<>();
        for (int col = 0; col < width; col++) {
            List<Integer> nextClue = null;
            for (int row = 0; row < height; row++) {
                int offset = row * width + col;
                if (solution[offset] == '.') {
                    if (nextClue != null && nextClue.size() > 2) {
                        downClues.add(nextClue);
                    }
                    nextClue = null;
                } else {
                    if (nextClue == null) {
                        nextClue = new ArrayList<>();
                    }
                    nextClue.add(offset);
                }
            }
            if (nextClue != null && nextClue.size() > 2) {
                downClues.add(nextClue);
            }
        }

        if (acrossClues.size() + downClues.size() != numClues) {
            throw new IOException(String.format(
                    "Wrong number of clues: expected %d, but had %d across and %d down (%d total)",
                    numClues, acrossClues.size(), downClues.size(),
                    acrossClues.size() + downClues.size()));
        }

        // Sort down clues in row-major order for clue assignment.
        Collections.sort(downClues, (clue1, clue2) -> clue1.get(0).compareTo(clue2.get(0)));

        // For each group of cells, assign it to a ClueViewModel.
        int acrossIndex = 0;
        int downIndex = 0;
        int clueNumber = 1;
        for (int clueIndex = 0; clueIndex < clueTexts.length; clueIndex++) {
            Clue clue = clues[clueIndex];
            if (acrossIndex >= acrossClues.size()) {
                // No more across clues
                setClueInfo(clue, false, clueNumber++, clueIndex, downClueMapping,
                            downClues.get(downIndex++));
            } else if (downIndex >= downClues.size()) {
                // No more down clues
                setClueInfo(clue, true, clueNumber++, clueIndex, acrossClueMapping,
                            acrossClues.get(acrossIndex++));
            } else {
                List<Integer> acrossCells = acrossClues.get(acrossIndex);
                List<Integer> downCells = downClues.get(downIndex);
                int cmp = acrossCells.get(0).compareTo(downCells.get(0));
                if (cmp == 0) {
                    // Across and down start on the same square and share a clue number.
                    setClueInfo(clue, true, clueNumber, clueIndex, acrossClueMapping, acrossCells);
                    clue = clues[++clueIndex];
                    setClueInfo(clue, false, clueNumber++, clueIndex, downClueMapping, downCells);
                    acrossIndex++;
                    downIndex++;
                } else if (cmp <= 0) {
                    // Across clue.
                    setClueInfo(clue, true, clueNumber++, clueIndex, acrossClueMapping,
                                acrossCells);
                    acrossIndex++;
                } else {
                    // Down clue.
                    setClueInfo(clue, false, clueNumber++, clueIndex, downClueMapping, downCells);
                    downIndex++;
                }
            }
        }

        // Identify clues that reference each other and persist this information so it can be
        // displayed.
        boolean[][] clueReferences = findClueReferences(clues);

        byte[][] rebusUserEntries = new byte[width * height][];
        TimerInfo timerInfo = null;
        for (Section section : extraSections) {
            switch (section.name) {
            case RUSR_SECTION_NAME:
                LittleEndianDataInputStream stream =
                        new LittleEndianDataInputStream(new ByteArrayInputStream(section.data));
                for (int i = 0; i < width * height; i++) {
                    byte[] rebusEntry = readNullTerminatedByteString(stream);
                    rebusUserEntries[i] = rebusEntry.length == 0 ? null : rebusEntry;
                }
                break;
            case LTIM_SECTION_NAME:
                String encodedTime = new String(section.data, StandardCharsets.US_ASCII);
                String[] tokens = encodedTime.split(",");
                long elapsedTimeSecs = Long.parseLong(tokens[0]);
                boolean isRunning = tokens[1].contentEquals("0");
                if (elapsedTimeSecs == 0) {
                    // Always default to running at the start.
                    isRunning = true;
                }
                timerInfo = new TimerInfo(elapsedTimeSecs, isRunning);
                break;
            }
        }

        if (timerInfo == null) {
            timerInfo = new TimerInfo(0, true);
        }

        return new PuzFile(fileChecksum, magic, headerChecksum, maskedChecksums, versionStringBytes,
                           includeNoteInTextChecksum, scrambledChecksum, width, height, numClues,
                           unknownBitmask, scrambledTag, solution, puzzleState, title, author,
                           copyright, clues, note, extraSections, acrossClueMapping,
                           downClueMapping, rebusUserEntries, clueReferences, timerInfo);
    }

    private static boolean[][] findClueReferences(Clue[] clues) {
        boolean[][] clueReferences = new boolean[clues.length][clues.length];
        Pattern pattern = Pattern.compile(".*\\b(\\d+)-(Across|across|Down|down)\\b.*");
        for (int i = 0; i < clues.length; i++) {
            Clue clue = clues[i];
            Matcher m = pattern.matcher(clue.getText());
            if (m.matches()) {
                Log.i(TAG,
                      "Pattern matched! " + clue.getNumber() + " " + m.group(1) + " " + m.group(2));
                int num = Integer.parseInt(Objects.requireNonNull(m.group(1)));
                boolean across = Objects.requireNonNull(m.group(2)).equalsIgnoreCase("Across");
                Log.i(TAG, "Searching for clue " + num + "-" + (across ? "Across" : "Down"));
                for (int j = 0; j < clues.length; j++) {
                    Clue otherClue = clues[j];
                    if (otherClue.getNumber() == num && across == otherClue.isAcross()) {
                        Log.i(TAG, "Found the matching clue: " + otherClue.getText());
                        clueReferences[i][j] = true;
                    }
                }
            }
        }
        return clueReferences;
    }

    /* Helper method for clue assignment. */
    private static void setClueInfo(Clue clue, boolean isAcross, int clueNumber, int clueIndex,
                                    int[] mapping, List<Integer> offsets) {
        clue.setAcross(isAcross);
        clue.setNumber(clueNumber);
        for (int offset : offsets) {
            mapping[offset] = clueIndex + 1;
        }
    }

    /**
     * Loads a puz file and double checks all file checksums.
     *
     * @param inputStream puzzle file data
     * @return parsed puz file
     * @throws IOException if loading fails or any checksum is wrong
     */
    public static PuzFile verifyPuzFile(InputStream inputStream) throws IOException {
        PuzFile puzzleLoader = loadPuzFile(inputStream);

        // Verify header checksum.
        final int headerChecksum = puzzleLoader.getHeaderChecksum();
        final int computedHeaderChecksum = puzzleLoader.computeHeaderChecksum();
        if (headerChecksum != computedHeaderChecksum) {
            throw new IOException(String.format("Bad header checksum. Expected 0x%04X, got 0x%04X",
                                                headerChecksum, computedHeaderChecksum));
        }

        // Verify masked checksums.
        final byte[] maskedChecksums = puzzleLoader.getMaskedChecksums();
        final byte[] computedMaskedChecksums = puzzleLoader.computeMaskedChecksums();
        for (int i = 0; i < 8; i++) {
            if (maskedChecksums[i] != computedMaskedChecksums[i]) {
                throw new IOException(
                        String.format("Bad masked checksum at bit %d. Expected 0x%02X, got 0x%02X",
                                      i, maskedChecksums[i], computedMaskedChecksums[i]));
            }
        }

        // Verify file checksum.
        final int fileChecksum = puzzleLoader.getFileChecksum();
        final int computedFileChecksum = puzzleLoader.computeFileChecksum();
        if (fileChecksum != computedFileChecksum) {
            throw new IOException(
                    String.format("Bad file checksum. Expected 0x%04X, got 0x%04X", fileChecksum,
                                  computedFileChecksum));
        }

        // Verify extra section checksums.
        for (Section section : puzzleLoader.mExtraSections) {
            int computedChecksum = checksumRegion(section.data, 0);
            if (section.checksum != computedChecksum) {
                throw new IOException(
                        String.format("Bad checksum for section %s: expected %04X, got %04X",
                                      section.name, section.checksum, computedChecksum));
            }
        }

        // Verify clue assignment.
        Clue[] clues = puzzleLoader.mClues;
        for (int i = 0; i < clues.length; i++) {
            Clue clue = clues[i];
            if (clue.getNumber() == 0) {
                throw new IOException("Missing clue number for clue index" + i);
            }
        }

        return puzzleLoader;
    }

    private static byte[] readNullTerminatedByteString(
            LittleEndianDataInputStream dataInputStream) throws IOException {
        byte[] bytes = new byte[8];
        int length = 0;
        while (true) {
            byte b = dataInputStream.readByte();
            if (b == 0) {
                break;
            }
            if (length >= bytes.length) {
                bytes = Arrays.copyOf(bytes, length * 2);
            }
            bytes[length++] = b;
        }
        return Arrays.copyOf(bytes, length);
    }

    private static void writeNullTerminatedByteString(byte[] bytes,
                                                      LittleEndianDataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.write(bytes);
        dataOutputStream.writeByte(0);
    }

    private static int checksumRegion(byte[] data, int cksum) {
        for (int i = 0; i < data.length; i++) {
            cksum = checksumByte(data[i], cksum);
        }
        return cksum;
    }

    private static int checksumByte(byte b, int cksum) {
        if ((cksum & 0x1) != 0) {
            cksum >>= 1;
            cksum += 0x8000;
        } else {
            cksum >>= 1;
        }
        cksum += (0xFF & b);
        cksum &= 0xFFFF;
        return cksum;
    }

    private static int checksumShort(int i, int cksum) {
        byte b1 = (byte) (i & 0xFF);
        byte b2 = (byte) ((i >> 8) & 0xFF);
        cksum = checksumByte(b1, cksum);
        cksum = checksumByte(b2, cksum);
        return cksum;
    }

    /**
     * Whether to include the note when checksumming the puzzle text (true for versions >= 1.3).
     *
     * @param versionStringBytes 4-byte version string
     * @return true if version >= 1.3
     * @throws IOException if version string cannot be parsed
     */
    private static boolean includeNoteInTextChecksum(byte[] versionStringBytes) throws IOException {
        final String versionString = new String(versionStringBytes, ISO_8859_1);
        String[] versionParts = versionString.split("\\.", 2);
        if (versionParts.length < 2) {
            throw new IOException(String.format("Bad version string: \"%s\"", versionString));
        }
        return versionParts[0].compareTo("1") > 0 ||
                (versionParts[0].compareTo("1") == 0 && versionParts[1].compareTo("3") >= 0);
    }

    /**
     * Checks whether we already have a copy of this file, possibly modified by the solver.
     */
    public boolean checkDuplicate(PuzFile other) {
        return getHeaderChecksum() == other.getHeaderChecksum() &&
                getTitle().equals(other.getTitle()) && getAuthor().equals(other.getAuthor());
    }

    @Override
    public String getAuthor() {
        return new String(mAuthor, ISO_8859_1);
    }

    @Override
    public String getCopyright() {
        return new String(mCopyright, ISO_8859_1);
    }

    @Override
    public String getNote() {
        return new String(mNote, ISO_8859_1);
    }

    @Override
    public void savePuzzleFile(OutputStream outputStream) throws IOException {
        LittleEndianDataOutputStream dataOutputStream =
                new LittleEndianDataOutputStream(outputStream);

        dataOutputStream.writeShort(computeFileChecksum());
        writeNullTerminatedByteString(mMagic, dataOutputStream);
        dataOutputStream.writeShort(computeHeaderChecksum());
        dataOutputStream.write(computeMaskedChecksums());
        dataOutputStream.write(mVersionString);
        dataOutputStream.writeShort(0);
        dataOutputStream.writeShort(mScrambledChecksum);
        byte[] junk = new byte[12];
        dataOutputStream.write(junk);
        dataOutputStream.writeByte(mWidth);
        dataOutputStream.writeByte(mHeight);
        dataOutputStream.writeShort(mNumClues);
        dataOutputStream.writeShort(mUnknownBitmask);
        dataOutputStream.writeShort(mScrambledTag);
        dataOutputStream.write(mSolution);
        dataOutputStream.write(mGrid);
        writeNullTerminatedByteString(mTitle, dataOutputStream);
        writeNullTerminatedByteString(mAuthor, dataOutputStream);
        writeNullTerminatedByteString(mCopyright, dataOutputStream);
        for (Clue clue : mClues) {
            byte[] bytes = clue.getText().getBytes(ISO_8859_1);
            writeNullTerminatedByteString(bytes, dataOutputStream);
        }
        writeNullTerminatedByteString(mNote, dataOutputStream);

        // Write extra sections.
        writeSectionIfPresent(GRBS_SECTION_NAME, dataOutputStream);
        writeSectionIfPresent(RTBL_SECTION_NAME, dataOutputStream);
        writeUserRebusSection(dataOutputStream);
        writeSectionIfPresent(GEXT_SECTION_NAME, dataOutputStream);
        writeTimerSection(dataOutputStream);
    }

    private void writeSectionIfPresent(String sectionName,
                                       LittleEndianDataOutputStream dataOutputStream) throws IOException {
        Section section = findSection(sectionName);
        if (section != null) {
            writeSection(sectionName, section.data, dataOutputStream);
        }
    }

    private void writeUserRebusSection(
            LittleEndianDataOutputStream dataOutputStream) throws IOException {
        ByteArrayOutputStream rusrSectionData = new ByteArrayOutputStream();
        boolean shouldWrite = false;
        for (byte[] userRebusEntry : mUserRebusEntries) {
            if (userRebusEntry != null) {
                shouldWrite = true;
                rusrSectionData.write(userRebusEntry);
            }
            rusrSectionData.write('\0');
        }
        if (shouldWrite) {
            writeSection(RUSR_SECTION_NAME, rusrSectionData.toByteArray(), dataOutputStream);
        }
    }

    private void writeTimerSection(
            LittleEndianDataOutputStream dataOutputStream) throws IOException {
        if (mTimerInfo == null) {
            return;
        }
        String data = String.format(Locale.getDefault(), "%d,%s", mTimerInfo.elapsedTimeSecs,
                                    mTimerInfo.isRunning ? "0" : "1");
        writeSection(LTIM_SECTION_NAME, data.getBytes(StandardCharsets.US_ASCII), dataOutputStream);
    }

    private void writeSection(String name, byte[] data,
                              LittleEndianDataOutputStream outputStream) throws IOException {
        outputStream.write(name.getBytes());
        outputStream.writeShort(data.length);
        outputStream.writeShort(checksumRegion(data, 0));
        outputStream.write(data);
        outputStream.write('\0');
    }

    public String getMagic() {
        return new String(mMagic, ISO_8859_1);
    }

    public String getVersionString() {
        return new String(mVersionString, ISO_8859_1);
    }

    public int getHeaderChecksum() {
        return mHeaderChecksum;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public int getNumClues() {
        return mNumClues;
    }

    public int getUnknownBitmask() {
        return mUnknownBitmask;
    }

    public int getScrambledTag() {
        return mScrambledTag;
    }

    public int computeHeaderChecksum() {
        int cksum;
        cksum = checksumByte(mWidth, 0);
        cksum = checksumByte(mHeight, cksum);
        cksum = checksumShort(mNumClues, cksum);
        cksum = checksumShort(mUnknownBitmask, cksum);
        cksum = checksumShort(mScrambledTag, cksum);
        return cksum;
    }

    public byte[] getMaskedChecksums() {
        return mMaskedChecksums;
    }

    private int computeTextChecksum(int cksum) {
        // For some reason, the title/author/copyright/note include the null terminator in the
        // computation, but not the clues.
        if (mTitle.length > 0) {
            cksum = checksumRegion(mTitle, cksum);
            cksum = checksumByte((byte) 0, cksum);
        }
        if (mAuthor.length > 0) {
            cksum = checksumRegion(mAuthor, cksum);
            cksum = checksumByte((byte) 0, cksum);
        }
        if (mCopyright.length > 0) {
            cksum = checksumRegion(mCopyright, cksum);
            cksum = checksumByte((byte) 0, cksum);
        }
        for (Clue clue : mClues) {
            cksum = checksumRegion(clue.getText().getBytes(ISO_8859_1), cksum);
        }

        if (mIncludeNoteInTextChecksum && mNote.length > 0) {
            cksum = checksumRegion(mNote, cksum);
            cksum = checksumByte((byte) 0, cksum);
        }

        return cksum;
    }

    public byte[] computeMaskedChecksums() {
        final int headerChecksum = computeHeaderChecksum();
        final int solutionChecksum = checksumRegion(mSolution, 0);
        final int gridChecksum = checksumRegion(mGrid, 0);
        final int partialChecksum = computeTextChecksum(0);

        final byte[] computedMaskedChecksums = new byte[8];
        computedMaskedChecksums[0] = (byte) ('I' ^ (headerChecksum & 0xFF));
        computedMaskedChecksums[1] = (byte) ('C' ^ (solutionChecksum & 0xFF));
        computedMaskedChecksums[2] = (byte) ('H' ^ (gridChecksum & 0xFF));
        computedMaskedChecksums[3] = (byte) ('E' ^ (partialChecksum & 0xFF));
        computedMaskedChecksums[4] = (byte) ('A' ^ ((headerChecksum >> 8) & 0xFF));
        computedMaskedChecksums[5] = (byte) ('T' ^ ((solutionChecksum >> 8) & 0xFF));
        computedMaskedChecksums[6] = (byte) ('E' ^ ((gridChecksum >> 8) & 0xFF));
        computedMaskedChecksums[7] = (byte) ('D' ^ ((partialChecksum >> 8) & 0xFF));

        return computedMaskedChecksums;
    }

    public int getFileChecksum() {
        return mFileChecksum;
    }

    public int computeFileChecksum() {
        int cksum = computeHeaderChecksum();
        cksum = checksumRegion(mSolution, cksum);
        cksum = checksumRegion(mGrid, cksum);
        cksum = computeTextChecksum(cksum);
        return cksum;
    }

    @Override
    public String getTitle() {
        return new String(mTitle, ISO_8859_1);
    }

    @Override
    public boolean isBlack(int row, int col) {
        return mSolution[getOffset(row, col)] == '.';
    }

    private int getOffset(int row, int col) {
        return row * mWidth + col;
    }

    @Override
    public String getCellSolution(int row, int col) {
        byte solution = mSolution[getOffset(row, col)];
        return new String(new byte[]{solution}, ISO_8859_1);
    }

    @Override
    public String getCellContents(int row, int col) {
        int offset = getOffset(row, col);
        if (mUserRebusEntries[offset] != null) {
            return new String(mUserRebusEntries[offset], ISO_8859_1);
        }
        byte contents = mGrid[offset];
        if (contents == '-') {
            return "";
        }
        return new String(new byte[]{contents}, ISO_8859_1);
    }

    @Override
    public void setCellContents(int row, int col, String value) {
        byte shortEntry;
        if (value.isEmpty()) {
            shortEntry = '-';
        } else {
            shortEntry = value.toUpperCase().getBytes(ISO_8859_1)[0];
        }
        mGrid[getOffset(row, col)] = shortEntry;

        if (value.length() > 1) {
            mUserRebusEntries[getOffset(row, col)] = value.getBytes(ISO_8859_1);
        } else {
            mUserRebusEntries[getOffset(row, col)] = null;
        }
    }

    @Override
    public boolean isSolved() {
        switch (getScrambleState()) {
        case UNSCRAMBLED:
            return Arrays.equals(mSolution, mGrid);
        case SCRAMBLED:
            return getComputedScrambledChecksum() == mScrambledChecksum;
        default:
            return false;
        }
    }

    @Override
    public boolean isEmpty() {
        for (byte b : mGrid) {
            if (!(b == '.' || b == '-')) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isCorrect(int row, int col) {
        switch (getScrambleState()) {
        case UNSCRAMBLED:
            int offset = getOffset(row, col);
            return mSolution[offset] == mGrid[offset];
        default:
            return true;
        }
    }

    @Override
    public String getSolution(int row, int col) {
        return new String(new byte[]{mSolution[getOffset(row, col)]}, ISO_8859_1);
    }

    @Override
    public TimerInfo getTimerInfo() {
        return mTimerInfo;
    }

    @Override
    public void setTimerInfo(TimerInfo timerInfo) {
        mTimerInfo = timerInfo;
    }

    private int getComputedScrambledChecksum() {
        int computedScrambledChecksum = 0;
        for (int i = 0; i < mWidth; i++) {
            for (int j = 0; j < mHeight; j++) {
                byte contents = mGrid[getOffset(j, i)];
                if (contents == '.') {
                    continue;
                }
                computedScrambledChecksum = checksumByte(contents, computedScrambledChecksum);
            }
        }
        return computedScrambledChecksum;
    }

    @Override
    public ScrambleState getScrambleState() {
        switch (mScrambledTag) {
        case 0x0:
            return ScrambleState.UNSCRAMBLED;
        case 0x2:
            return ScrambleState.LOCKED;
        case 0x4:
            return ScrambleState.SCRAMBLED;
        default:
            return ScrambleState.UNKNOWN;
        }
    }

    private Section findSection(String sectionName) {
        for (Section section : mExtraSections) {
            if (section.name.equals(sectionName)) {
                return section;
            }
        }
        return null;
    }

    public int getNumRebusSquares() {
        Section grbsSection = findSection(GRBS_SECTION_NAME);
        if (grbsSection == null) {
            return 0;
        }
        int numRebusSquares = 0;
        for (byte b : grbsSection.data) {
            if (b != 0) {
                numRebusSquares++;
            }
        }
        return numRebusSquares;
    }

    public String getRbtlString() {
        Section rbtlSection = findSection(RTBL_SECTION_NAME);
        if (rbtlSection == null) {
            return "";
        }
        return new String(rbtlSection.data, StandardCharsets.US_ASCII);
    }

    public String getRebus(int rebusIndex) {
        String rbtlString = getRbtlString();
        int offset = 0;
        while (offset < rbtlString.length()) {
            int i = rbtlString.indexOf(';', offset);
            if (i >= 0) {
                int index = Integer.parseInt(rbtlString.substring(offset, offset + 2).trim());
                if (index == rebusIndex + 1) {
                    return rbtlString.substring(offset + 3, i);
                }
            }
            offset = i + 1;
        }
        return null;
    }

    public byte getGextMask(int row, int col) {
        Section gextSection = findSection(GEXT_SECTION_NAME);
        if (gextSection == null) {
            return GEXT_MASK_NONE;
        }
        return gextSection.data[getOffset(row, col)];
    }

    @Override
    public boolean isCircled(int row, int col) {
        return (getGextMask(row, col) & GEXT_MASK_CIRCLED) != 0;
    }

    @Override
    public Clue getClue(int i) {
        return mClues[i];
    }

    @Override
    public int getAcrossClueIndex(int row, int col) {
        return mAcrossClueMapping[getOffset(row, col)] - 1;
    }

    @Override
    public int getDownClueIndex(int row, int col) {
        return mDownClueMapping[getOffset(row, col)] - 1;
    }

    @Override
    public boolean[][] getClueReferences() {
        return mClueReferences;
    }

    public ImmutableSet<String> getSectionNames() {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (Section section : mExtraSections) {
            builder.add(section.name);
        }
        return builder.build();
    }

    public String getSectionAsText(String name) {
        for (Section section : mExtraSections) {
            if (section.name.equals(name)) {
                return new String(section.data, ISO_8859_1);
            }
        }
        return "";
    }

    private static class Section implements Serializable {
        final String name;
        final int length;
        final int checksum;
        final byte[] data;

        public Section(String name, int length, int checksum, byte[] data) {
            this.name = name;
            this.length = length;
            this.checksum = checksum;
            this.data = data;
        }

        public static Section readSection(
                LittleEndianDataInputStream dataInputStream) throws IOException {
            final byte[] sectionNameBytes = new byte[4];
            if (dataInputStream.read(sectionNameBytes) < 4) {
                // No more data
                return null;
            }

            String sectionName = new String(sectionNameBytes);
            int length = dataInputStream.readUnsignedShort();
            int checksum = dataInputStream.readUnsignedShort();
            byte[] data = new byte[length];
            if (dataInputStream.read(data) < length) {
                throw new EOFException();
            }
            dataInputStream.readByte();  // null terminator
            Section section = new Section(sectionName, length, checksum, data);
            return section;
        }
    }
}
