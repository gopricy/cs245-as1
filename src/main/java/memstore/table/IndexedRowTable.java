package memstore.table;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import memstore.data.ByteFormat;
import memstore.data.DataLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * IndexedRowTable, which stores data in row-major format.
 * That is, data is laid out like
 *   row 1 | row 2 | ... | row n.
 *
 * Also has a tree index on column `indexColumn`, which points
 * to all row indices with the given value.
 */
public class IndexedRowTable implements Table {

    int numCols;
    int numRows;
    private TreeMap<Integer, IntArrayList> index;
    private ByteBuffer rows;
    private int indexColumn;

    public IndexedRowTable(int indexColumn) {
        this.indexColumn = indexColumn;
    }

    /**
     * Loads data into the table through passed-in data loader. Is not timed.
     *
     * @param loader Loader to load data from.
     * @throws IOException
     */
    @Override
    public void load(DataLoader loader) throws IOException {
        this.numCols = loader.getNumCols();
        List<ByteBuffer> rows = loader.getRows();
        numRows = rows.size();
        this.rows = ByteBuffer.allocate(ByteFormat.FIELD_LEN * numRows * numCols);
        this.index = new TreeMap<>();

        for (int rowId = 0; rowId < numRows; rowId++) {
            ByteBuffer curRow = rows.get(rowId);
            for (int colId = 0; colId < numCols; colId++) {
                int offset = ByteFormat.FIELD_LEN * ((rowId * numCols) + colId);
                int value = curRow.getInt(ByteFormat.FIELD_LEN * colId);
                this.rows.putInt(offset, value);
                if(colId == this.indexColumn){
                    if (this.index.get(value) == null){
                        this.index.put(value, new IntArrayList());
                    }
                    this.index.get(value).add(rowId);
                }
            }
        }
    }

    /**
     * Returns the int field at row `rowId` and column `colId`.
     */
    @Override
    public int getIntField(int rowId, int colId) {
        int offset = ByteFormat.FIELD_LEN * ((rowId * numCols) + colId);
        return this.rows.getInt(offset);
    }

    /**
     * Inserts the passed-in int field at row `rowId` and column `colId`.
     */
    @Override
    public void putIntField(int rowId, int colId, int field) {
        int offset = ByteFormat.FIELD_LEN * ((rowId * numCols) + colId);
        this.rows.putInt(offset, field);
    }

    /**
     * Implements the query
     *  SELECT SUM(col0) FROM table;
     *
     *  Returns the sum of all elements in the first column of the table.
     */
    @Override
    public long columnSum() {
        int sum = 0;
        for(int i = 0; i < this.numRows; i++){
            sum += this.getIntField(i, 0);
        }
        return sum;
    }

    /**
     * Implements the query
     *  SELECT SUM(col0) FROM table WHERE col1 > threshold1 AND col2 < threshold2;
     *
     *  Returns the sum of all elements in the first column of the table,
     *  subject to the passed-in predicates.
     */
    @Override
    public long predicatedColumnSum(int threshold1, int threshold2) {
        int sum = 0;
        if (this.indexColumn != 1 && this.indexColumn != 2){
            for(int i = 0; i < this.numRows; i++){
                if (this.getIntField(i, 1) <= threshold1 || this.getIntField(i, 2) >= threshold2){
                    continue;
                }
                sum += this.getIntField(i, 0);
            }
        }
        //TODO: Boning needs this to be fixed
        return sum;
    }

    /**
     * Implements the query
     *  SELECT SUM(col0) + SUM(col1) + ... + SUM(coln) FROM table WHERE col0 > threshold;
     *
     *  Returns the sum of all elements in the rows which pass the predicate.
     */
    @Override
    public long predicatedAllColumnsSum(int threshold) {
        int sum = 0;
        if(this.indexColumn == 0){
            TreeSet<Integer> selectedKeys = new TreeSet<>();
            NavigableMap<Integer, IntArrayList> tail = this.index.tailMap(threshold, false);
            Collection<IntArrayList> arrays = tail.values();
            for (IntArrayList i: arrays){
                selectedKeys.addAll(i);
            }

            for (int i : selectedKeys) {
                for (int j = 0; j < this.numCols; j++) {
                    sum += this.getIntField(i, j);
                }
            }
        } else{
            for(int i = 0; i < this.numRows; i++){
                if (this.getIntField(i, 0) <= threshold){
                    continue;
                }
                for(int j = 0; j < this.numCols; j++){
                    sum += this.getIntField(i, j);
                }
            }
        }
        return sum;
    }

    /**
     * Implements the query
     *   UPDATE(col3 = col3 + col2) WHERE col0 < threshold;
     *
     *   Returns the number of rows updated.
     */
    @Override
    public int predicatedUpdate(int threshold) {
        int updateNum = 0;
        TreeSet<Integer> selectedKeys = new TreeSet<>();
        NavigableMap<Integer, IntArrayList> tail = this.index.headMap(threshold, false);
        Collection<IntArrayList> arrays = tail.values();
        for (IntArrayList i: arrays){
            selectedKeys.addAll(i);
        }
        for (int i : selectedKeys) {
                int col2 = this.getIntField(i, 2);
                int col3 = this.getIntField(i, 3);
                this.putIntField(i, 3, col2 + col3);
                updateNum += 1;
        }
        return updateNum;
    }
}
