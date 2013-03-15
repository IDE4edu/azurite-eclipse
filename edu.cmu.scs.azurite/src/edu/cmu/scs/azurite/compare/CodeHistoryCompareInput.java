package edu.cmu.scs.azurite.compare;

import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.compare.structuremergeviewer.ICompareInputChangeListener;
import org.eclipse.swt.graphics.Image;

public class CodeHistoryCompareInput implements ICompareInput {
	
	private ITypedElement mLeft;
	private ITypedElement mRight;
	
	public CodeHistoryCompareInput(ITypedElement left, ITypedElement right) {
		mLeft = left;
		mRight = right;
	}

	@Override
	public String getName() {
		return "Particl Code History";
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public int getKind() {
		return 0;
	}

	@Override
	public ITypedElement getAncestor() {
		return null;
	}

	@Override
	public ITypedElement getLeft() {
		return mLeft;
	}

	@Override
	public ITypedElement getRight() {
		return mRight;
	}

	@Override
	public void addCompareInputChangeListener(
			ICompareInputChangeListener listener) {
	}

	@Override
	public void removeCompareInputChangeListener(
			ICompareInputChangeListener listener) {
	}

	@Override
	public void copy(boolean leftToRight) {
	}

}
