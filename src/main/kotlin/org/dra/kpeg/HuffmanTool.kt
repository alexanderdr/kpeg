package org.dra.kpeg

import java.util.*
import org.dra.kpeg.util.getBit
import org.dra.kpeg.util.getBitAsOne
import java.nio.ByteBuffer

/**
 * Created by Derek Alexander
 */

class HuffmanTool {
    companion object {
        fun buildTree(input: ByteArray): ProtoNode {
            val leaves = createSortedList(input)

            return createTreeFromSortedList(leaves)
        }

        //create a tree where no leaf is more than 16 bits deep
        //and for each bit length of N, a key composed entirely of N 1s is an inner node.
        fun buildJpegFriendlyTree(input: ByteArray): ProtoNode {
            var leaves = createSortedList(input)

            var tree = createTreeFromSortedList(leaves)
            while(depth(tree) > 16) {
                //flatten the heuristic we are using...  There might be a better way of flattening, but this is probably fine...
                leaves = leaves.map { Math.ceil(it.first / 2.0).toInt() to it.second }.toMutableList()
                tree = createTreeFromSortedList(leaves)
            }

            //pull the rightmost leaf off the tree and split a depth - 1 node to contain that leaf and its previous value
            var current = tree
            var last = tree
            while(current !is LeafNode && current is InnerNode && current.right != null) {
                last = current
                current = current.right!!
            }

            //This happens when there is only one node in the tree
            if(current != tree) {
                if(last is InnerNode) { //it always is...
                    last.right = null
                }

                val flattenedList = buildLengthList(tree)

                var currentPlaced = false
                for (i in 0 until flattenedList.size) {
                    if (flattenedList[i].first < 16) {
                        val n = flattenedList[i].second
                        //right != null produces better results when the depth is less than 16
                        //otherwise we can end up in a situation where we have a layer with only one inner node in it
                        if (n.left is LeafNode && n.right != null) {
                            currentPlaced = true
                            val newInner = InnerNode(n.left, current)
                            n.left = newInner
                            break
                        }
                    }
                }

                if (!currentPlaced) {
                    if (last is InnerNode) {
                        //uh, just make a new Inner node and put it back where we found it...
                        last.right = InnerNode(current, null)
                    } else {
                        throw IllegalStateException("???")
                    }

                    if (depth(tree) > 16) {
                        throw IllegalStateException("Internal error: huffman tree is too deep after leaf manipulation!")
                    }
                }
            }
            //end the leaf switch logic

            //turn it into a table and back...

            return HuffmanTable(tree).createTree()
        }

        private fun buildLengthList(node: ProtoNode): List<Pair<Int, InnerNode>> {
            val res = mutableListOf<Pair<Int, InnerNode>>()
            fun visitAllNodes(depth: Int, current: ProtoNode) {
                if(current is InnerNode) {
                    visitAllNodes(depth + 1, current.left)
                    current.right?.let {
                        visitAllNodes(depth + 1, it)
                    }
                    res.add(depth to current)
                }
            }

            visitAllNodes(1, node)
            res.sortByDescending { it.first }

            return res
        }

        fun depth(node: ProtoNode, depth: Int = 0): Int {
            return if(node is InnerNode) {
                val rightDepth = node.right?.let { depth(it, depth + 1) } ?:  0
                Math.max(depth(node.left, depth + 1), rightDepth)
            } else {
                depth //how many edges have been traversed
            }
        }

        private fun createSortedList(input: ByteArray): MutableList<Pair<Int, ProtoNode>> {
            val incidences = IntArray(256)
            for (i in 0 until 256) {
                incidences[i] = 0
            }

            input.forEach {
                incidences[it.toInt() and 0xFF]++
            }

            @Suppress("UnnecessaryVariable")
            val leaves = incidences.mapIndexed { index, frequency -> frequency to leafNodeOf(index.toByte()) }
                    .filter { it.first != 0 }
                    .sortedBy { it.first }
                    .toMutableList()
            return leaves
        }

        private fun createTreeFromSortedList(list: List<Pair<Int, ProtoNode>>): ProtoNode {
            val leaves = list.toMutableList()

            val nodeList = mutableListOf<Pair<Int, ProtoNode>>()

            if(list.size == 1) {
                return InnerNode(list[0].second, null)
            }

            while (leaves.size > 0 || nodeList.size > 1) {

                fun pick(): Pair<Int, ProtoNode> {
                    // Nesting these reduces legibility
                    @Suppress("LiftReturnOrAssignment")
                    if (leaves.size > 0 && nodeList.size > 0) {
                        //break ties preferring leaf nodes (leads to a flatter tree)
                        return if (leaves[0].first <= nodeList[0].first) {
                            leaves.removeAt(0)
                        } else {
                            nodeList.removeAt(0)
                        }
                    } else if (leaves.size > 0) {
                        return leaves.removeAt(0)
                    } else {
                        return nodeList.removeAt(0)
                    }
                }

                //pick the two smallest
                val left = pick()
                val right = pick()

                val sum = left.first + right.first

                val leftNode = left.second
                val rightNode = right.second

                //prefer to have leaves on the left and branches on the right
                val node = if(leftNode is InnerNode && rightNode !is InnerNode) {
                    internalNodeOf(rightNode, leftNode)
                } else {
                    internalNodeOf(leftNode, rightNode)
                }

                nodeList.add(sum to node)
            }

            //nodeList[0] is our tree
            return nodeList[0].second
        }

        fun encode(data: ByteArray, root: ProtoNode): IntArray {
            val result = mutableListOf<Int>()
            var index = 0
            var offset = 0
            result.add(index, 0)

            val encodingMap = generateMap(root)

            fun addBits(item: Byte) {
                val bits = encodingMap[item] ?: throw IllegalArgumentException("No leaf in node to encode value '$item'")

                val value = bits.value
                val len = bits.length

                if(len + offset >= 32) {
                    //split
                    val secondBitsLength = (len+offset) - 32
                    val firstBitsLength = len - secondBitsLength
                    val (firstBitsValue, secondBitsValue) = bits.splitValue(firstBitsLength)

                    val inv = (32 - offset) - firstBitsLength
                    result[index] = result[index] or (firstBitsValue shl inv)
                    index++
                    result.add(index, 0)
                    offset = 0
                    result[index] = result[index] or (secondBitsValue shl (32 - secondBitsLength))
                    offset += secondBitsLength
                } else {
                    val inv = (32 - offset) - len
                    result[index] = result[index] or (value shl inv)
                    offset += len

                    if(offset == 32) {
                        index++
                        result.add(index, 0)
                        offset = 0
                    }
                }
            }

            data.forEach {
                addBits(it)
            }

            return result.toIntArray()
        }

        //Returns value, bits read
        private fun decodeOneItem(data: IntArray, bitOffset: Int, root: ProtoNode): Pair<Byte, Int> {
            var current = root
            var bit = bitOffset

            while(true) {
                if (current is InnerNode) {
                    current = if (data.getBit(bit) == 0) {
                        current.left
                    } else {
                        if (current.right != null) {
                            current.right!!
                        } else {
                            throw IllegalStateException("Attempted to read right node when there is none")
                        }
                    }
                    bit++
                } else {
                    throw UnknownError("Unknown current node type when decoding from Huffman tree, expecting InnerNode")
                }

                if (current is LeafNode) {
                    return current.value to bit
                }
            }
        }

        //todo: combine this with the above
        fun decodeOneItem(data: ByteBuffer, bitOffset: Int, root: ProtoNode): Pair<Byte, Int> {
            var current = root
            var bit = bitOffset

            if (current is LeafNode) {
                return current.value to bit + 1
            }

            while(true) {
                if (current is InnerNode) {
                    current = if (data.getBitAsOne(bit) == 0) {
                        current.left
                    } else {
                        if (current.right != null) {
                            current.right!!
                        } else {
                            throw IllegalStateException("Attempted to read right node when there is none bitIndex: $bit")
                        }
                    }
                    bit++
                } else {
                    throw UnknownError("Unknown current node type when decoding from Huffman tree, expecting InnerNode")
                }

                if (current is LeafNode) {
                    return current.value to bit
                }
            }
        }

        fun decode(data: IntArray, items: Int, root: ProtoNode): ByteArray {
            val output = ByteArray(items)

            var bit = 0
            var writtenItems = 0

            while(writtenItems < items) {
                val (item, currentBit) = decodeOneItem(data, bit, root)
                bit = currentBit
                output[writtenItems] = item
                writtenItems++
            }

            return output
        }

        fun generateMap(root: ProtoNode): Map<Byte, Bits> {
            val map = mutableMapOf<Byte, Bits>()

            rGenMap(root, map, Bits(0, 0))

            return map
        }

        private fun rGenMap(root: ProtoNode, map: MutableMap<Byte, Bits>, current: Bits) {
            if(root is LeafNode) {
                map[root.value] = current
                return
            } else if (root is InnerNode) {
                rGenMap(root.left, map, current.addZero())
                if(root.right != null) {
                    rGenMap(root.right!!, map, current.addOne())
                }
            }
        }

        class Bits(val value: Int, val length: Int) {
            fun addOne(): Bits {
                return Bits((value shl 1) or 1, length + 1)
            }

            fun addZero(): Bits {
                // yes, this is a no-op
                return Bits((value shl 1) or 0, length + 1)//Bits(value or (0 shl length), length + 1)
            }

            fun splitValue(firstBitsLength: Int): Pair<Int, Int> {
                /*val earlyBits = value and ((1 shl (firstBitsLength)) - 1)
                val remainingBits = (value and ((1 shl (firstBitsLength)) - 1).inv()) ushr firstBitsLength
                return earlyBits to remainingBits*/
                val lowerMask = ((1 shl (length - firstBitsLength)) - 1)
                val earlyBits = (value and lowerMask.inv()) ushr (length - firstBitsLength)
                val remainingBits = value and lowerMask

                return earlyBits to remainingBits
            }
        }

        @Suppress("MemberVisibilityCanPrivate", "unused")
        fun printNodes(cur: ProtoNode, byteString: String = "", spacing: Int = 0) {
            print(" ".repeat(spacing))

            if(cur is LeafNode) {
                println("$byteString: ${cur.value}")
            } else if(cur is InnerNode) {
                println("_")
                printNodes(cur.left, byteString + "0", spacing + 2)
                if(cur.right != null) {
                    printNodes(cur.right!!, byteString + "1", spacing + 2)
                }
            }
        }

        fun treesEqual(first: ProtoNode?, second: ProtoNode?): Boolean {
            if((first is InnerNode && second !is InnerNode) || (first is LeafNode && second !is LeafNode)) {
                return false
            }

            //technically the second clause is redundant but the compiler can't figure it out
            if(first is InnerNode && second is InnerNode) {
                return treesEqual(first.left, second.left) && treesEqual(first.right, second.right)
            }

            if(first is LeafNode && second is LeafNode) {
                return first.value == second.value
            }

            return true //we get here it both are null or ProtoNodes (somehow)
        }

        private fun leafNodeOf(value: Byte): ProtoNode {
            return LeafNode(value)
        }

        private fun internalNodeOf(left: ProtoNode, right: ProtoNode?): ProtoNode {
            return InnerNode(left, right)
        }

        open class ProtoNode {

            fun <T> fold(start: T, action: (T, ProtoNode) -> T): T {
                if(this is InnerNode) {
                    var res = action(start, this)
                    res = left.fold(res, action)
                    right?.let {
                        return it.fold(res, action)
                    } ?: return res
                } else {
                    return action(start, this)
                }
            }
        }

        class InnerNode(var left: ProtoNode, var right: ProtoNode?): ProtoNode()
        class LeafNode(val value: Byte): ProtoNode()
    }

    class HuffmanTable(data: ArrayList<ArrayList<Byte>?>) {
        val table = data
        private var tree: ProtoNode? = null

        constructor(root: ProtoNode): this(ArrayList<ArrayList<Byte>?>()) {
            for(i in 0..16) {
                table.add(arrayListOf())
            }
            populateTable(root)
        }

        //build tree from this mess, bottom up
        fun createTree(): ProtoNode {
            val temp = tree
            if(temp != null) {
                return temp
            }

            var lowerList = arrayListOf<ProtoNode>()
            var nextList = arrayListOf<ProtoNode>()
            for(i in table.size - 1 downTo 0) {

                val list = table[i]

                if(list != null) {
                    @Suppress("LoopToCallChain")
                    for (j in 0 until list.size) {
                        nextList.add(LeafNode(list[j]))
                    }
                }

                //doing this second means we get the inner nodes on the right which is the correct order
                while(lowerList.size > 0){
                    //make inner nodes out of them, adding them to the nextList

                    val first = lowerList.removeAt(0)
                    val second = if(!lowerList.isEmpty()) { lowerList.removeAt(0) } else { null }
                    nextList.add(InnerNode(first, second))
                }

                lowerList = nextList
                nextList = arrayListOf()
            }


            val res = if(lowerList.size > 1) {
                if(lowerList.size > 2) {
                    throw IllegalStateException("Unexpected internal state when building Huffman tree")
                }
                InnerNode(lowerList[0], lowerList[1])
            } else {
                lowerList[0]
            }
            tree = res
            return res
        }

        private fun populateTable(root: ProtoNode) {
            var curLevel = arrayListOf(root)
            var next = arrayListOf<ProtoNode>()

            var depth = 0
            while(!curLevel.isEmpty()) {
                val current = curLevel.removeAt(0)
                if (current is InnerNode) {
                    next.add(current.left)
                    current.right?.let { next.add(it) }
                } else if (current is LeafNode) {
                    if(table[depth] == null) {
                        table[depth] = arrayListOf()
                    }

                    table[depth]?.add(current.value)
                }

                if(curLevel.isEmpty()) {
                    curLevel = next
                    next = arrayListOf()
                    depth++
                }
            }
        }

        val totalSize: Int
            get() = table.fold(0, {value, item -> value + (item?.size ?: 0)})

    }

    @Suppress("unused", "MemberVisibilityCanPrivate")
    fun ProtoNode.forEach(action: (ProtoNode) -> Unit) {
        if(this is InnerNode) {
            left.forEach(action)
            action(this)
            right?.forEach(action)
        } else {
            action(this)
        }
    }
}
