import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	public int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			freq[val] = freq[val] + 1;
		}
		freq[PSEUDO_EOF] = 1;
		return freq;
	}
	public HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int k = 0; k < counts.length; k++) {
			if (counts[k]>0) {
				pq.add(new HuffNode(k,counts[k],null,null));
				}
			}
		if (myDebugLevel >=DEBUG_HIGH) {
			System.out.printf("pq created wtih %d nodes \n", pq.size());
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(left.myValue + right.myValue, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	public String[] makeCodingsFromTree(HuffNode root){
		String[] encodings = new String[ALPH_SIZE+1];
		codingHelper(root,"",encodings);
		return encodings;
	}
	public void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myRight == null && root.myLeft == null) {
			encodings[root.myValue] = path + "1";
			
			if (myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s \n", root.myValue,path);
			}
			
			return;
		}
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path +"1", encodings);
		
		return;
	}
	public void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myRight != null || root.myLeft != null) {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}
	public void writeCompressedBits(String[] encoding, BitInputStream in, BitOutputStream out) {
		
		while (true) {
			int bits = in.readBits(8);
			if (bits == -1) break;
			String code = encoding[bits];
			out.writeBits(code.length(), Integer.parseInt(code, 2));

			}
		
		String code = encoding[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}
	public HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("bits cannot be read");
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int val = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(val, 0, null, null);
		}

	}
	public void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode curr = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) curr = curr.myLeft;
				else curr = curr.myRight;
				
				if (curr.myLeft == null && curr.myRight == null) {
					if (curr.myValue == PSEUDO_EOF) break;
					else {
						out.writeBits(BITS_PER_WORD, curr.myValue);
						curr = root;
					}
				}
			}
		}
	}
	
}