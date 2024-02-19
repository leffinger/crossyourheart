package io.github.leffinger.crossyourheart.io;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and updates Across Lite (puz) files.
 *
 * <p>Many thanks to the authors of this explainer for the file format:
 * https://code.google.com/archive/p/puz/wikis/FileFormat.wiki
 */
@SuppressWarnings("UnstableApiUsage")
public class PuzFile extends AbstractPuzzleFile {
    public static final byte GEXT_MASK_CIRCLED = (byte) 0x80;
    public static final byte GEXT_MASK_NONE = 0x0;
    private static final String MAGIC = "ACROSS&DOWN";
    private static final String GRBS_SECTION_NAME = "GRBS";
    private static final String RTBL_SECTION_NAME = "RTBL";
    private static final String RUSR_SECTION_NAME = "RUSR";
    private static final String LTIM_SECTION_NAME = "LTIM";
    private static final String GEXT_SECTION_NAME = "GEXT";
    final int mFileChecksum;
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
    final Map<String, Section> mExtraSections;
    final int[] mAcrossClueMapping;
    final int[] mDownClueMapping;
    final byte[][] mUserRebusEntries;
    final boolean[][] mClueReferences;
    final String[] mSolutionWithRebuses;
    TimerInfo mTimerInfo;

    public PuzFile(InputStream inputStream) throws IOException {
        LittleEndianDataInputStream dataInputStream = new LittleEndianDataInputStream(inputStream);

        // Get header info.
        mFileChecksum = dataInputStream.readUnsignedShort();
        byte[] magic = readNullTerminatedByteString(dataInputStream);

        // Error out early if the magic string is wrong (not a puz file at all).
        String magicString = new String(magic, ISO_8859_1);
        if (!magicString.equals(MAGIC)) {
            throw new IOException("Wrong file magic; is this a puz file?");
        }

        mHeaderChecksum = dataInputStream.readUnsignedShort();
        mMaskedChecksums = new byte[8];
        if (dataInputStream.read(mMaskedChecksums) == -1) {
            throw new EOFException();
        }
        mVersionString = new byte[4];
        if (dataInputStream.read(mVersionString) == -1) {
            throw new EOFException();
        }

        mIncludeNoteInTextChecksum = includeNoteInTextChecksum();
        if (dataInputStream.skipBytes(2) != 2) {  // skip junk
            throw new EOFException();
        }
        mScrambledChecksum = dataInputStream.readUnsignedShort();
        if (dataInputStream.skipBytes(12) != 12) {  // skip junk
            throw new EOFException();
        }

        // Get basic puzzle info: width, height, etc.
        mWidth = dataInputStream.readByte();
        mHeight = dataInputStream.readByte();
        mNumClues = dataInputStream.readUnsignedShort();
        mUnknownBitmask = dataInputStream.readUnsignedShort();
        mScrambledTag = dataInputStream.readUnsignedShort();

        // Read the solution grid and puzzle state.
        int puzzleSize = mWidth * mHeight;
        mSolution = new byte[puzzleSize];
        if (dataInputStream.read(mSolution) < puzzleSize) {
            throw new EOFException();
        }
        mGrid = new byte[puzzleSize];
        if (dataInputStream.read(mGrid) < puzzleSize) {
            throw new EOFException();
        }

        // Read variable-length strings: title, author, clues, etc.
        mTitle = readNullTerminatedByteString(dataInputStream);
        mAuthor = readNullTerminatedByteString(dataInputStream);
        mCopyright = readNullTerminatedByteString(dataInputStream);
        final byte[][] clueTexts = new byte[mNumClues][];
        for (int i = 0; i < mNumClues; i++) {
            clueTexts[i] = readNullTerminatedByteString(dataInputStream);
        }
        mNote = readNullTerminatedByteString(dataInputStream);

        // Read extra sections. Each of these start with a four-byte key with the name of the
        // section, and include a length and checksum.
        mExtraSections = new LinkedHashMap<>();  // maintains order of sections
        while (true) {
            Section section = Section.readSection(dataInputStream);
            if (section == null) {
                break;
            }
            mExtraSections.put(section.name, section);
        }
        mUserRebusEntries = getRebusUserEntries();
        mTimerInfo = parseTimerInfo();
        mSolutionWithRebuses = getSolutionWithRebuses();

        // Clue assignment. This is not part of the file format, but we do it here so that
        // (1) we don't have to redo this math whenever we create a ViewModel and (2) we can fail
        // loading if there is an issue assigning clues.
        mClues = new Clue[mNumClues];
        for (int i = 0; i < mNumClues; i++) {
            mClues[i] = new Clue(new String(clueTexts[i], ISO_8859_1));
        }
        // acrossClueMapping[offset] is 0 if no across clue is associated with that offset, and
        // clueIndex+1 otherwise. Same for downClueMapping.
        mAcrossClueMapping = new int[puzzleSize];
        mDownClueMapping = new int[puzzleSize];
        assignClues();

        // Identify clues that reference each other. Again, this isn't in the file format.
        mClueReferences = findClueReferences();
    }

    /**
     * Loads a puz file and double checks all file checksums.
     *
     * @param inputStream puzzle file data
     * @return parsed puz file
     * @throws IOException if loading fails or any checksum is wrong
     */
    public static PuzFile verifyPuzFile(InputStream inputStream) throws IOException {
        PuzFile puzzleLoader = new PuzFile(inputStream);

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
        for (Section section : puzzleLoader.mExtraSections.values()) {
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
        for (byte b : data) {
            cksum = checksumByte(b, cksum);
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
     * @return true if version >= 1.3
     * @throws IOException if version string cannot be parsed
     */
    private boolean includeNoteInTextChecksum() throws IOException {
        final String versionString = new String(mVersionString, ISO_8859_1);
        String[] versionParts = versionString.split("\\.", 2);
        if (versionParts.length < 2) {
            throw new IOException(String.format("Bad version string: \"%s\"", versionString));
        }
        return versionParts[0].compareTo("1") > 0 ||
                (versionParts[0].compareTo("1") == 0 && versionParts[1].compareTo("3") >= 0);
    }

    /**
     * Match clue texts with blank cells in the grid.
     *
     * <p>This method assumes that mWidth, mHeight, mClues, mNumClues, mAcrossClueMapping, and
     * mDownClueMapping have been initialized. It updates mClues, mAcrossClueMapping, and
     * mDownClueMapping.
     *
     * @throws IOException if unable to match
     */
    private void assignClues() throws IOException {
        ArrayList<CandidateClue> candidateClues = new ArrayList<>(mNumClues);

        // Find across clues (chunks of 1 or more contiguous cells).
        for (int row = 0; row < mHeight; row++) {
            int start = -1;
            for (int col = 0; col <= mWidth; col++) {
                if (col < mWidth && !isBlack(row, col)) {
                    // Empty cell.
                    if (start == -1) {
                        // Start of a new word.
                        start = col;
                    }
                } else {
                    // Black cell or end of row.
                    if (start != -1) {
                        candidateClues.add(new CandidateClue(getOffset(row, start), col - start, true));
                        start = -1;
                    }
                }
            }
        }

        // Find down clues.
        for (int col = 0; col < mWidth; col++) {
            int start = -1;
            for (int row = 0; row <= mHeight; row++) {
                if (row < mHeight && !isBlack(row, col)) {
                    // Empty cell.
                    if (start == -1) {
                        // Start of a new clue.
                        start = row;
                    }
                } else {
                    // Black cell or end of column.
                    if (start != -1) {
                        // end of the clue was at row-1
                        candidateClues.add(new CandidateClue(getOffset(start, col), row - start, false));
                        start = -1;
                    }
                }
            }
        }

        // Count the number of candidate clues that have length 1 or 2. Usually these will be
        // unclued, but we can check against the number of clues we have and infer the minimum clue
        // length for this puzzle.
        int lengthOne = 0;
        int lengthTwo = 0;
        int lengthThreeOrMore = 0;
        for (CandidateClue candidateClue : candidateClues) {
            if (candidateClue.length == 1) {
                lengthOne++;
            } else if (candidateClue.length == 2) {
                lengthTwo++;
            } else {
                lengthThreeOrMore++;
            }
        }

        int minLength;
        if (lengthThreeOrMore == mNumClues) {
            minLength = 3;
        } else if (lengthThreeOrMore + lengthTwo == mNumClues) {
            minLength = 2;
        } else if (lengthThreeOrMore + lengthTwo + lengthOne == mNumClues) {
            minLength = 1;
        } else {
            throw new IOException(String.format(
                    "Bad number of clues: expected %d; had %d of length>=3, %d of length 2, and %d of length 1",
                    mNumClues, lengthThreeOrMore, lengthTwo, lengthOne));
        }

        // Sort all clues in row-major order for clue assignment. This sort is stable, so if across
        // & down share a clue number, across will be first.
        //noinspection ComparatorCombinators
        Collections.sort(candidateClues, (clue1, clue2) -> clue1.offset - clue2.offset);

        // Build the acrossClueMapping and downClueMapping grids, while also assigning clue numbers.
        int clueIndex = 0;
        int clueNumber = 0;
        int previousOffset = -1;
        for (CandidateClue candidateClue : candidateClues) {
            if (candidateClue.length < minLength) {
                continue;
            }

            // This is for the case where across and down start at the same cell.
            if (candidateClue.offset > previousOffset) {
                clueNumber++;
            }
            previousOffset = candidateClue.offset;

            Clue clue = mClues[clueIndex++];
            clue.setAcross(candidateClue.isAcross);
            clue.setNumber(clueNumber);
            if (candidateClue.isAcross) {
                for (int i = 0; i < candidateClue.length; i++) {
                    int offset = candidateClue.offset + i;
                    mAcrossClueMapping[offset] = clueIndex;
                }
            } else {
                for (int i = 0; i < candidateClue.length; i++) {
                    int offset = candidateClue.offset + i * mWidth;
                    mDownClueMapping[offset] = clueIndex;
                }
            }
        }
    }

    private boolean[][] findClueReferences() {
        boolean[][] clueReferences = new boolean[mClues.length][mClues.length];
        Pattern pattern = Pattern.compile(".*\\b(\\d+)[ -](Across|across|Down|down)\\b.*");
        for (int i = 0; i < mClues.length; i++) {
            Clue clue = mClues[i];
            Matcher m = pattern.matcher(clue.getText());
            if (m.matches()) {
                int num = Integer.parseInt(Objects.requireNonNull(m.group(1)));
                boolean across = Objects.requireNonNull(m.group(2)).equalsIgnoreCase("Across");
                for (int j = 0; j < mClues.length; j++) {
                    Clue otherClue = mClues[j];
                    if (otherClue.getNumber() == num && across == otherClue.isAcross()) {
                        clueReferences[i][j] = true;
                    }
                }
            }
        }
        return clueReferences;
    }

    private byte[][] getRebusUserEntries() throws IOException {
        byte[][] rebusUserEntries = new byte[mWidth * mHeight][];
        if (mExtraSections.containsKey(RUSR_SECTION_NAME)) {
            Section rusrSection = mExtraSections.get(RUSR_SECTION_NAME);
            assert rusrSection != null;
            LittleEndianDataInputStream stream =
                    new LittleEndianDataInputStream(new ByteArrayInputStream(rusrSection.data));
            for (int i = 0; i < mWidth * mHeight; i++) {
                byte[] rebusEntry = readNullTerminatedByteString(stream);
                rebusUserEntries[i] = rebusEntry.length == 0 ? null : rebusEntry;
            }
        }
        return rebusUserEntries;
    }

    private TimerInfo parseTimerInfo() {
        if (!mExtraSections.containsKey((LTIM_SECTION_NAME))) {
            return new TimerInfo(0, true);
        }
        Section ltimSection = mExtraSections.get(LTIM_SECTION_NAME);
        assert ltimSection != null;
        String encodedTime = new String(ltimSection.data, US_ASCII);
        String[] tokens = encodedTime.split(",");
        long elapsedTimeSecs = Long.parseLong(tokens[0]);
        boolean isRunning = tokens[1].contentEquals("0");
        if (elapsedTimeSecs == 0) {
            // Always default to running at the start.
            isRunning = true;
        }
        return new TimerInfo(elapsedTimeSecs, isRunning);
    }

    private String[] getSolutionWithRebuses() throws IOException {
        String[] solutionWithRebuses = new String[mSolution.length];
        for (int i = 0; i < mSolution.length; i++) {
            solutionWithRebuses[i] = new String(new byte[]{mSolution[i]}, ISO_8859_1);
        }

        if (!(mExtraSections.containsKey(RTBL_SECTION_NAME) &&
                mExtraSections.containsKey(GRBS_SECTION_NAME))) {
            return solutionWithRebuses;
        }

        Map<Byte, String> rebusTable = new LinkedHashMap<>();
        String rtbl = new String(Objects.requireNonNull(mExtraSections.get(RTBL_SECTION_NAME)).data,
                US_ASCII);
        Pattern pattern = Pattern.compile("(\\d+):([^ ;]+)");
        Matcher m = pattern.matcher(rtbl);
        while (m.find()) {
            byte index = Byte.parseByte(Objects.requireNonNull(m.group(1)));
            String value = m.group(2);
            rebusTable.put(index, value);
        }

        byte[] grbs = Objects.requireNonNull(mExtraSections.get(GRBS_SECTION_NAME)).data;
        if (grbs.length != mSolution.length) {
            throw new IOException("Rebus table is the wrong size");
        }
        for (int i = 0; i < grbs.length; i++) {
            if (grbs[i] == 0) {
                continue;
            }
            byte index = (byte) (grbs[i] - 1);
            if (!rebusTable.containsKey(index)) {
                throw new IOException(
                        "Square should be a rebus, but there is no corresponding entry in the table");
            }
            solutionWithRebuses[i] = rebusTable.get(index);
        }

        return solutionWithRebuses;
    }

    @Override
    protected void savePuzzleFile(OutputStream outputStream) throws IOException {
        LittleEndianDataOutputStream dataOutputStream =
                new LittleEndianDataOutputStream(outputStream);

        dataOutputStream.writeShort(computeFileChecksum());
        writeNullTerminatedByteString(MAGIC.getBytes(ISO_8859_1), dataOutputStream);
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

    private void writeTimerSection(
            LittleEndianDataOutputStream dataOutputStream) throws IOException {
        if (mTimerInfo == null) {
            return;
        }
        String data = String.format(Locale.getDefault(), "%d,%s", mTimerInfo.elapsedTimeSecs,
                                    mTimerInfo.isRunning ? "0" : "1");
        writeSection(LTIM_SECTION_NAME, data.getBytes(US_ASCII), dataOutputStream);
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

    @Override
    public String getCellSolution(int row, int col) {
        return mSolutionWithRebuses[getOffset(row, col)];
    }

    private void writeSection(String name, byte[] data,
                              LittleEndianDataOutputStream outputStream) throws IOException {
        outputStream.write(name.getBytes());
        outputStream.writeShort(data.length);
        outputStream.writeShort(checksumRegion(data, 0));
        outputStream.write(data);
        outputStream.write('\0');
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
    public boolean isCorrect(int row, int col) {
        if (getScrambleState() != ScrambleState.UNSCRAMBLED) {
            return true;
        }
        String entry = getCellContents(row, col);
        String solution = mSolutionWithRebuses[getOffset(row, col)];
        return entry.equals(solution) || entry.equals(solution.substring(0, 1));
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
    public String getSolution(int row, int col) {
        return mSolutionWithRebuses[getOffset(row, col)];
    }

    private Section findSection(String sectionName) {
        for (Section section : mExtraSections.values()) {
            if (section.name.equals(sectionName)) {
                return section;
            }
        }
        return null;
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

    public String getRtblString() {
        Section rtblSection = findSection(RTBL_SECTION_NAME);
        if (rtblSection == null) {
            return "";
        }
        return new String(rtblSection.data, US_ASCII);
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

    public String getRebus(int rebusIndex) {
        String rtblString = getRtblString();
        int offset = 0;
        while (offset < rtblString.length()) {
            int i = rtblString.indexOf(';', offset);
            if (i >= 0) {
                int index = Integer.parseInt(rtblString.substring(offset, offset + 2).trim());
                if (index == rebusIndex + 1) {
                    return rtblString.substring(offset + 3, i);
                }
            }
            offset = i + 1;
        }
        return null;
    }

    public ImmutableSet<String> getSectionNames() {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (Section section : mExtraSections.values()) {
            builder.add(section.name);
        }
        return builder.build();
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

    public String getSectionAsText(String name) {
        for (Section section : mExtraSections.values()) {
            if (section.name.equals(name)) {
                return new String(section.data, ISO_8859_1);
            }
        }
        return "";
    }

    private static class CandidateClue {
        int offset;
        int length;
        boolean isAcross;

        CandidateClue(int offset, int length, boolean isAcross) {
            this.offset = offset;
            this.length = length;
            this.isAcross = isAcross;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CandidateClue that = (CandidateClue) o;
            return offset == that.offset && length == that.length && isAcross == that.isAcross;
        }

        @Override
        public String toString() {
            return String.format("offset=%d,length=%d,across=%b", offset, length, isAcross);
        }
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
            return new Section(sectionName, length, checksum, data);
        }
    }
}
