import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <p>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 *
 * @author Owen Astrachan
 * @author Darren Li
 * <p>
 * Revise
 */

public class HuffProcessor {

    private class HuffNode implements Comparable<HuffNode> {
        HuffNode left;
        HuffNode right;
        int value;
        int weight;

        public HuffNode(int val, int count) {
            value = val;
            weight = count;
        }

        public HuffNode(int val, int count, HuffNode ltree, HuffNode rtree) {
            value = val;
            weight = count;
            left = ltree;
            right = rtree;
        }

        public int compareTo(HuffNode o) {
            return weight - o.weight;
        }
    }

    public static final int BITS_PER_WORD = 8;
    public static final int BITS_PER_INT = 32;
    public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
    public static final int PSEUDO_EOF = ALPH_SIZE;
    public static final int HUFF_NUMBER = 0xface8200;
    public static final int HUFF_TREE = HUFF_NUMBER | 1;

    private boolean myDebugging = false;

    public HuffProcessor() {
        this(false);
    }

    public HuffProcessor(boolean debug) {
        myDebugging = debug;
    }

    /**
     * Compresses a file. Process must be reversible and loss-less.
     *
     * @param in  Buffered bit stream of the file to be compressed.
     * @param out Buffered bit stream writing to the output file.
     */
    public void compress(BitInputStream in, BitOutputStream out) {
        HuffNode root = makeTree(in);
        //writing huffman encoding ID header
        out.writeBits(BITS_PER_INT, HUFF_TREE);
        String[] encodings = new String[ALPH_SIZE + 1];
        makeEncodings(root, "", encodings);

        int bits = in.readBits(BITS_PER_WORD);

        while(bits != -1){
//            String encoding = encodings[Integer.parseInt(String.valueOf(bits), 2)];
            String encoding = encodings[bits];
            for(char c : encoding.toCharArray()){
                out.writeBits(1, Character.getNumericValue(c));
            }

            bits = in.readBits(BITS_PER_WORD);
        }

        //TODO fix this bug, pseudo is a blank string for some reason but should not be
        String pseudo = encodings[PSEUDO_EOF];

        for(char c : pseudo.toCharArray()){
            out.writeBits(1, Character.getNumericValue(c));
        }

        out.close();
    }

    /**
     *
     * @param in BitInputStream input
     * @return a HuffNode binary tree where each HuffNode's value is the character represented as a base 10 integer and its weight is the number of occurrences of that character
     */
    private HuffNode makeTree(BitInputStream in) {
        TreeMap<Integer, Integer> freqs = new TreeMap<>();
        ArrayList<HuffNode> nodes = new ArrayList<>();
        in.reset();
        int bits = in.readBits(BITS_PER_WORD);
        while (bits != -1) {
            freqs.put(bits, freqs.getOrDefault(bits, 0) + 1);
            bits = in.readBits(BITS_PER_WORD);
        }

        for (Map.Entry<Integer, Integer> entry : freqs.entrySet()) {
            nodes.add(new HuffNode(entry.getKey(), entry.getValue(), null, null));
        }
        nodes.add(new HuffNode(PSEUDO_EOF, 1, null, null));

        //either of these two works, but actually not needed because priority queue sorts for us
//        Collections.sort(nodes, Comparator.naturalOrder());
//        Collections.sort(nodes, (HuffNode a, HuffNode b) -> a.compareTo(b));

        PriorityQueue<HuffNode> pq = new PriorityQueue<>(nodes);

        while (pq.size() > 1) {
            HuffNode a = pq.remove();
            HuffNode b = pq.remove();

            HuffNode bruh = new HuffNode(0, a.weight + b.weight, a, b);
            pq.add(bruh);
            System.out.println(bruh.value);
        }

        in.reset();
        printTree(pq.peek());
        return pq.remove();
    }

    private void printTree(HuffNode root){
        if(root == null) return;
        if(root.left == null && root.right == null){
            System.out.println(root.value);
        }
        printTree(root.left);
        printTree(root.right);
    }

    private void makeEncodings(HuffNode root, String path, String[] encodings){

        if(root.left == null && root.right == null){
            // TODO: check if this works if root.value is in binary (should work as provided, but why)
            encodings[root.value] = path;
//            encodings[Integer.parseInt(String.valueOf(root.value),2)] = path;
            return;
        }
        if(root.left != null){
            makeEncodings(root.left, path + 0, encodings);
        }
        if(root.right != null){
            makeEncodings(root.right, path + 1, encodings);
        }
        System.out.println(Arrays.toString(encodings));
    }

    /**
     * Decompresses a file. Output file must be identical bit-by-bit to the
     * original.
     *
     * @param in  Buffered bit stream of the file to be decompressed.
     * @param out Buffered bit stream writing to the output file.
     */
    public void decompress(BitInputStream in, BitOutputStream out) {

        int bits = in.readBits(BITS_PER_INT);
        if (bits != HUFF_TREE) throw new HuffException("invalid magic number " + bits);

        HuffNode root = readTree(in);
        HuffNode current = root;
        while (true) {
            int nextBit = in.readBits(1);
            if (nextBit == -1) throw new HuffException(("Bad input, no PSEUDO_EOF"));

            if (nextBit == 1) {
                current = current.right;
            } else if (nextBit == 0) {
                current = current.left;
            }
            if(current == null) break;

            if (current.left == null && current.right == null) {
                if (current.value == PSEUDO_EOF) break;
                else {
                    out.writeBits(BITS_PER_WORD, current.value);
                    current = root;
                }
            }

        }
        out.close();


        // remove all code when implementing decompress
/*
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();

 */
    }

    private HuffNode readTree(BitInputStream in) {
        int bit = in.readBits(1);
        if (bit == -1) throw new HuffException("invalid magic number " + bit);
        if (bit == 0) {
            HuffNode left = readTree(in);
            HuffNode right = readTree(in);
            return new HuffNode(0, 0, left, right);
        } else {
            var value = in.readBits(BITS_PER_WORD + 1);
            return new HuffNode(value, 0, null, null);
        }
    }
}