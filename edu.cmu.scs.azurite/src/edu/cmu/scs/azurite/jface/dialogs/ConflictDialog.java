package edu.cmu.scs.azurite.jface.dialogs;

import java.util.List;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import edu.cmu.scs.azurite.commands.runtime.RuntimeDC;
import edu.cmu.scs.azurite.model.undo.Chunk;
import edu.cmu.scs.azurite.model.undo.UndoAlternative;
import edu.cmu.scs.fluorite.util.Utilities;

@SuppressWarnings("restriction")
public class ConflictDialog extends TitleAreaDialog {
	
	private static final int MINIMUM_OPERATIONS_HEIGHT = 50;
	
	private static final int MINIMUM_PREVIEW_WIDTH = 700;
	private static final int MINIMUM_PREVIEW_HEIGHT = 500;
	
	private static final int MARGIN_WIDTH = 10;
	private static final int MARGIN_HEIGHT = 10;
	
	private static final int SPACING = 10;
	
	private static final String TEXT = "Selective Undo";
	private static final String TITLE = "Conflict Detected";
	private static final String DEFAULT_MESSAGE = "One or more conflicts were detected while performing selective undo.";
			
	private IDocument mOriginalDoc;
	private IDocument mCopyDoc;
	private int mOffset;
	private int mLength;
	private int mOriginalLength;
	private List<UndoAlternative> mAlternatives;
	private Chunk mChunk;
	
	private ISourceViewer mCodePreview;
	private Color mBackground;

	public ConflictDialog(Shell parent, IDocument originalDoc,
			int offset, int length, List<UndoAlternative> alternatives, Chunk chunk) {
		super(parent);
		
		mOriginalDoc = originalDoc;
		mCopyDoc = new Document(originalDoc.get());
		mOffset = offset;
		mLength = length;
		mOriginalLength = length;
		mAlternatives = alternatives;
		mChunk = chunk;
		
		mBackground = new Color(parent.getDisplay(), 186, 205, 224);
		
		setHelpAvailable(false);
	}

	@Override
	public void create() {
		super.create();
		
		getShell().setText(TEXT);
		
		setTitle(TITLE);
		setMessage(DEFAULT_MESSAGE);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		// Use GridLayout.
		GridLayout gridLayout = new GridLayout(3, false);
		gridLayout.marginWidth = MARGIN_WIDTH;
		gridLayout.marginHeight = MARGIN_HEIGHT;
		gridLayout.horizontalSpacing = SPACING;
		gridLayout.verticalSpacing = SPACING;
		parent.setLayout(gridLayout);
		
		// Involved Operations Group
		createInvolvedOperationsGroup(parent);
		
		// Code Preview Group
		createCodePreviewGroup(parent);
		
		// Alternatives Group
		createAlternativesGroup(parent);
		
		return parent;
	}

	private void createInvolvedOperationsGroup(Composite parent) {
		Composite groupOperations = new Composite(parent, SWT.NONE);
		
		GridLayout gridLayout = new GridLayout();
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		groupOperations.setLayout(gridLayout);
		
		GridData gridData = new GridData(GridData.FILL, GridData.FILL, true, true, 3, 1);
		gridData.minimumHeight = MINIMUM_OPERATIONS_HEIGHT;
		groupOperations.setLayoutData(gridData);
		
		
		// Add the label.
		Label label = new Label(groupOperations, SWT.NONE);
		label.setText("The following operations were selected but had some conflicts outside of the selection.");
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		// Add the list.
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List(groupOperations, SWT.V_SCROLL);
		list.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		for (RuntimeDC dc : mChunk.getInvolvedChanges()) {
			list.add(dc.toString());
		}
	}

	private void createCodePreviewGroup(Composite parent) {
		Group groupPreview = new Group(parent, SWT.NONE);
		groupPreview.setText("Code Preview");
		
		FillLayout fillLayout = new FillLayout();
		fillLayout.marginWidth = MARGIN_WIDTH;
		fillLayout.marginHeight = MARGIN_HEIGHT;
		groupPreview.setLayout(fillLayout);
		
		GridData gridData = new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1);
		gridData.minimumWidth = MINIMUM_PREVIEW_WIDTH;
		gridData.minimumHeight = MINIMUM_PREVIEW_HEIGHT;
		groupPreview.setLayoutData(gridData);
		
		
		// Create the Java Source Viewer.
		CompositeRuler ruler = new CompositeRuler();
		
		LineNumberRulerColumn lnrc = new LineNumberRulerColumn();
		lnrc.setFont(Utilities.getFont());
		
		ruler.addDecorator(0, lnrc);
		
		mCodePreview = new SourceViewer(groupPreview,
				ruler, SWT.H_SCROLL | SWT.V_SCROLL);
		
		// Setting up the Java Syntax Highlighting
		JavaTextTools tools = JavaPlugin.getDefault().getJavaTextTools();
		tools.setupJavaDocumentPartitioner(mCopyDoc);

		JavaSourceViewerConfiguration config =
				new JavaSourceViewerConfiguration(
						tools.getColorManager(),
						JavaPlugin.getDefault().getCombinedPreferenceStore(),
						null, null);
		
		mCodePreview.configure(config);
		mCodePreview.setDocument(mCopyDoc);
		mCodePreview.setEditable(false);
		mCodePreview.getTextWidget().setFont(Utilities.getFont());
	}

	private void createAlternativesGroup(Composite parent) {
		Group groupAlternatives = new Group(parent, SWT.NONE);
		groupAlternatives.setText("Alternatives");
		
		RowLayout rowLayout = new RowLayout(SWT.VERTICAL);
		rowLayout.fill = true;
		rowLayout.pack = false;
		rowLayout.spacing = SPACING;
		rowLayout.marginWidth = MARGIN_WIDTH;
		rowLayout.marginHeight = MARGIN_HEIGHT;
		
		groupAlternatives.setLayout(rowLayout);
		groupAlternatives.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true, 1, 1));
		
		// Add alternative Buttons
		// TODO replace the radio buttons with prettier custom buttons.
		for (int i = 0; i < mAlternatives.size(); ++i) {
			UndoAlternative alternative = mAlternatives.get(i);
			
			Button buttonAlternative = new Button(groupAlternatives, SWT.RADIO);
			buttonAlternative.setText(alternative.getResultingCode());
			buttonAlternative.setToolTipText(alternative.getDescription());
			
			if (i == 0) {
				buttonAlternative.setSelection(true);
			}
			
			final int currentIndex = i;
			buttonAlternative.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					selectAlternative(currentIndex);
				}
			});
		}
		
		selectAlternative(0);
	}

	private void selectAlternative(int index) {
		if (index < 0 || index >= mAlternatives.size()) {
			throw new IllegalArgumentException();
		}
		
		UndoAlternative alternative = mAlternatives.get(index);
		
		try {
			mCopyDoc.replace(mOffset, mLength, alternative.getResultingCode());
		} catch (BadLocationException e) {
			e.printStackTrace();
			return;
		}
		
		mLength = alternative.getResultingCode().length();
		
		// Highlight the area!
		StyleRange range = new StyleRange(mOffset, mLength, null, mBackground);
		mCodePreview.getTextWidget().setStyleRange(range);
	}
	
	@Override
	protected void cancelPressed() {
		super.cancelPressed();
	}

	@Override
	protected void okPressed() {
		try {
			mOriginalDoc.replace(mOffset, mOriginalLength, mCopyDoc.get(mOffset, mLength));
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
		
		super.okPressed();
	}

	@Override
	protected boolean isResizable() {
		return true;
	}
	
}
