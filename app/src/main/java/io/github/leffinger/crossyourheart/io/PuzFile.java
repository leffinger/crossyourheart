package io.github.leffinger.crossyourheart.io;

import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PuzFile extends AbstractPuzzleFile {
    private static final String MAGIC = "ACROSS&DOWN";

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
    final byte[][] mClues;
    final byte[] mNote;

    public PuzFile(int fileChecksum, byte[] magic, int headerChecksum, byte[] maskedChecksums,
                   byte[] versionString, boolean includeNoteInTextChecksum, int scrambledChecksum,
                   byte width, byte height, int numClues, int unknownBitmask, int scrambledTag,
                   byte[] solution, byte[] grid, byte[] title, byte[] author, byte[] copyright,
                   byte[][] clues, byte[] note) {
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
        mClues = clues;
        mNote = note;
    }

    public static PuzFile loadPuzFile(InputStream inputStream) throws IOException {
        LittleEndianDataInputStream dataInputStream = new LittleEndianDataInputStream(inputStream);

        // Get header info.
        final int fileChecksum = dataInputStream.readUnsignedShort();
        final byte[] magic = readNullTerminatedByteString(dataInputStream);

        // Error out early if the magic string is wrong (not a puz file at all).
        String magicString = new String(magic, StandardCharsets.ISO_8859_1);
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
        final byte[][] clues = new byte[numClues][];
        for (int i = 0; i < numClues; i++) {
            clues[i] = readNullTerminatedByteString(dataInputStream);
        }
        final byte[] note = readNullTerminatedByteString(dataInputStream);

        return new PuzFile(fileChecksum, magic, headerChecksum, maskedChecksums, versionStringBytes,
                           includeNoteInTextChecksum, scrambledChecksum, width, height, numClues,
                           unknownBitmask, scrambledTag, solution, puzzleState, title, author,
                           copyright, clues, note);
    }

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

    private static int cksum_region(byte[] data, int cksum) {
        for (int i = 0; i < data.length; i++) {
            cksum = cksum_byte(data[i], cksum);
        }
        return cksum;
    }

    private static int cksum_byte(byte b, int cksum) {
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

    private static int cksum_short(int i, int cksum) {
        byte b1 = (byte) (i & 0xFF);
        byte b2 = (byte) ((i >> 8) & 0xFF);
        cksum = cksum_byte(b1, cksum);
        cksum = cksum_byte(b2, cksum);
        return cksum;
    }

    private static boolean includeNoteInTextChecksum(byte[] versionStringBytes) throws IOException {
        final String versionString = new String(versionStringBytes, StandardCharsets.ISO_8859_1);
        String[] versionParts = versionString.split("\\.", 2);
        if (versionParts.length < 2) {
            throw new IOException(String.format("Bad version string: \"%s\"", versionString));
        }
        if (versionParts[0].compareTo("1") >= 0 && versionParts[1].compareTo("3") >= 0) {
            return true;
        }
        return false;
    }

    public boolean checkDuplicate(PuzFile other) {
        return getHeaderChecksum() == other.getHeaderChecksum() &&
                getTitle().equals(other.getTitle()) && getAuthor().equals(other.getAuthor());
    }

    @Override
    public String getAuthor() {
        return new String(mAuthor, StandardCharsets.ISO_8859_1);
    }

    @Override
    public String getCopyright() {
        return new String(mCopyright, StandardCharsets.ISO_8859_1);
    }

    @Override
    public String getNote() {
        return new String(mNote, StandardCharsets.ISO_8859_1);
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
        for (byte[] clue : mClues) {
            writeNullTerminatedByteString(clue, dataOutputStream);
        }
        writeNullTerminatedByteString(mNote, dataOutputStream);
    }

    public String getMagic() {
        return new String(mMagic, StandardCharsets.ISO_8859_1);
    }

    public String getVersionString() {
        return new String(mVersionString, StandardCharsets.ISO_8859_1);
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
        cksum = cksum_byte(mWidth, 0);
        cksum = cksum_byte(mHeight, cksum);
        cksum = cksum_short(mNumClues, cksum);
        cksum = cksum_short(mUnknownBitmask, cksum);
        cksum = cksum_short(mScrambledTag, cksum);
        return cksum;
    }

    public byte[] getMaskedChecksums() {
        return mMaskedChecksums;
    }

    private int computeTextChecksum(int cksum) {
        // For some reason, the title/author/copyright/note include the null terminator in the
        // computation, but not the clues.
        if (mTitle.length > 0) {
            cksum = cksum_region(mTitle, cksum);
            cksum = cksum_byte((byte) 0, cksum);
        }
        if (mAuthor.length > 0) {
            cksum = cksum_region(mAuthor, cksum);
            cksum = cksum_byte((byte) 0, cksum);
        }
        if (mCopyright.length > 0) {
            cksum = cksum_region(mCopyright, cksum);
            cksum = cksum_byte((byte) 0, cksum);
        }
        for (byte[] clue : mClues) {
            cksum = cksum_region(clue, cksum);
        }

        if (mIncludeNoteInTextChecksum && mNote.length > 0) {
            cksum = cksum_region(mNote, cksum);
            cksum = cksum_byte((byte) 0, cksum);
        }

        return cksum;
    }

    public byte[] computeMaskedChecksums() {
        final short headerChecksum = (short) computeHeaderChecksum();
        final short solutionChecksum = (short) cksum_region(mSolution, 0);
        final short gridChecksum = (short) cksum_region(mGrid, 0);
        final short partialChecksum = (short) computeTextChecksum(0);

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
        cksum = cksum_region(mSolution, cksum);
        cksum = cksum_region(mGrid, cksum);
        cksum = computeTextChecksum(cksum);
        return cksum;
    }

    @Override
    public String getTitle() {
        return new String(mTitle, StandardCharsets.ISO_8859_1);
    }

    @Override
    public String getClue(int i) {
        return new String(mClues[i], StandardCharsets.ISO_8859_1);
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
        return new String(new byte[]{solution}, StandardCharsets.ISO_8859_1);
    }

    @Override
    public String getCellContents(int row, int col) {
        byte contents = mGrid[getOffset(row, col)];
        if (contents == '-') {
            return "";
        }
        return new String(new byte[]{contents}, StandardCharsets.ISO_8859_1);
    }

    @Override
    public void setCellContents(int row, int col, String value) {
        byte representation;
        if (value.isEmpty()) {
            representation = '-';
        } else {
            representation = value.toUpperCase().getBytes(StandardCharsets.ISO_8859_1)[0];
        }
        mGrid[getOffset(row, col)] = representation;

        // Update checksums? (Will happen automatically when puzzle is reloaded from disk - maybe
        // not needed here.)
    }

    @Override
    public boolean isSolved() {
        if (mScrambledTag == 0) {
            return Arrays.equals(mSolution, mGrid);
        }

        int computedScrambledChecksum = 0;
        for (int i = 0; i < mWidth; i++) {
            for (int j = 0; j < mHeight; j++) {
                byte contents = mGrid[getOffset(j, i)];
                if (contents == '.') {
                    continue;
                }
                computedScrambledChecksum = cksum_byte(contents, computedScrambledChecksum);
            }
        }
        return computedScrambledChecksum == mScrambledChecksum;
    }

    public int getScrambledChecksum() {
        return mScrambledChecksum;
    }
}
