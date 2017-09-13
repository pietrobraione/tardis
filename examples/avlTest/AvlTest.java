package avlTest;

public class AvlTest {
	public void testIsEmpty(){
		AvlTree tree = new AvlTree();
		tree.isEmpty();
	}
	public void testMakeEmpty(){
		AvlTree tree = new AvlTree();
		tree.makeEmpty();
	}
	public void testFind(){
		AvlTree tree = new AvlTree();
		tree.insertElem(1);
		tree.find(1);
	}
	public void testFindMax(){
		AvlTree tree = new AvlTree();
		tree.insertElem(3);
		tree.findMax();
	}
	public void testInsertElem(){
		AvlTree tree = new AvlTree();
		tree.insertElem(10);
	}
}