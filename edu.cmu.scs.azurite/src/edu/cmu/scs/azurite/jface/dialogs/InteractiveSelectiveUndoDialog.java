package edu.cmu.scs.azurite.jface.dialogs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.CompareViewerSwitchingPane;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import edu.cmu.scs.azurite.commands.runtime.RuntimeDC;
import edu.cmu.scs.azurite.compare.AzuriteCompareInput;
import edu.cmu.scs.azurite.compare.AzuriteCompareLabelProvider;
import edu.cmu.scs.azurite.compare.SimpleCompareItem;
import edu.cmu.scs.azurite.jface.viewers.ChunksTreeViewer;
import edu.cmu.scs.azurite.model.FileKey;
import edu.cmu.scs.azurite.model.OperationId;
import edu.cmu.scs.azurite.model.RuntimeHistoryManager;
import edu.cmu.scs.azurite.model.undo.Chunk;
import edu.cmu.scs.azurite.model.undo.SelectiveUndoEngine;
import edu.cmu.scs.azurite.views.RectSelectionListener;
import edu.cmu.scs.azurite.views.TimelineViewPart;
import edu.cmu.scs.fluorite.util.Utilities;

public class InteractiveSelectiveUndoDialog extends TitleAreaDialog implements RectSelectionListener {
	
	private static final int DEFAULT_WIDTH = 800;
	private static final int DEFAULT_HEIGHT = 600;
	
	private static final int MINIMUM_CHUNKS_HEIGHT = 100;
	private static final int MINIMUM_BOTTOM_AREA_HEIGHT = 200;
	
	private static final int MARGIN_WIDTH = 10;
	private static final int MARGIN_HEIGHT = 10;
	
	private static final int SPACING = 10;
	
	private static final int SURROUNDING_CONTEXT_SIZE = 3;
	
	private static final String TEXT = "Azurite - Interactive Selective Undo";
	private static final String TITLE = "Interactive Selective Undo";
	private static final String DEFAULT_MESSAGE = "The preview will be updated as you select/deselect rectangles from the timeline.\nNOTE: Please do not edit source code while this dialog is open.";
	
	private static final String CHUNKS_TITLE = "Changes to be performed";
	
	protected static final String NEXT_CHANGE_ID = "edu.cmu.scs.azurite.nextChange";
	protected static final String NEXT_CHANGE_TOOLTOP = "Select Next Chunk";
	
	protected static final String PREVIOUS_CHANGE_ID = "edu.cmu.scs.azurite.previousChange";
	protected static final String PREVIOUS_CHANGE_TOOLTIP = "Select Previous Chunk";
	
	private static final String INFORMATION_SELECT_RECTS = "You must select some rectangles to undo.";
	private static final String INFORMATION_SELECT_CHUNK = "Select a chunk from the list on the top to see the preview.";
	
	private static InteractiveSelectiveUndoDialog instance = null;
	public static InteractiveSelectiveUndoDialog getInstance() {
		return instance;
	}

	private class NextChunk extends Action {
		public NextChunk() {
			setId(NEXT_CHANGE_ID);
			setImageDescriptor(CompareUI.DESC_ETOOL_NEXT);
			setDisabledImageDescriptor(CompareUI.DESC_DTOOL_NEXT);
			setHoverImageDescriptor(CompareUI.DESC_CTOOL_NEXT);
			setToolTipText(NEXT_CHANGE_TOOLTOP);
		}
		public void run() {
			mChunksTreeViewer.revealNext();
		}
	}

	private class PreviousChunk extends Action {
		public PreviousChunk() {
			setId(PREVIOUS_CHANGE_ID);
			setImageDescriptor(CompareUI.DESC_ETOOL_PREV);
			setDisabledImageDescriptor(CompareUI.DESC_DTOOL_PREV);
			setHoverImageDescriptor(CompareUI.DESC_CTOOL_PREV);
			setToolTipText(PREVIOUS_CHANGE_TOOLTIP);
		}
		public void run() {
			mChunksTreeViewer.revealPrevious();
		}
	}
	
	private class ChunksContentProvider implements ITreeContentProvider {
		
		private Map<FileKey, List<Chunk>> mInput;

		@Override
		public void dispose() {
			// Do nothing.
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			// Do nothing.
		}

		// Assume that inputElement is typed as Map<FileKey, List<Chunk>>
		@Override
		@SuppressWarnings("unchecked")
		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof Map<?, ?>) {
				mInput = (Map<FileKey, List<Chunk>>) inputElement;
				
				return mInput.keySet().toArray(new FileKey[mInput.keySet().size()]);
			} else {
				return null;
			}
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof FileKey) {
				if (mInput != null) {
					return mInput.get(parentElement).toArray();
				}
			}
			
			return null;
		}

		@Override
		public Object getParent(Object element) {
			if (element instanceof Chunk) {
				Chunk chunk = (Chunk) element;
				return chunk.getBelongsTo();
			}
			
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			if (element instanceof FileKey) {
				if (mInput != null && !mInput.get(element).isEmpty()) {
					return true;
				}
			}
			
			return false;
		}
		
	}
	
	private class ChunksLabelProvider extends LabelProvider {
		
		private Map<ImageDescriptor, Image> mImages;
		
		public ChunksLabelProvider() {
			mImages = new HashMap<ImageDescriptor, Image>();
		}

		@Override
		public Image getImage(Object element) {
			if (element instanceof FileKey) {
				FileKey fileKey = (FileKey) element;
				
				ImageDescriptor baseImage = PlatformUI.getWorkbench().getEditorRegistry().getImageDescriptor(fileKey.getFileNameOnly());
				// TODO determine whether there is a conflict, and draw overlay image.
				return getImage(baseImage);
			}
			else if (element instanceof Chunk) {
				Chunk chunk = (Chunk) element;
				
				ImageDescriptor errorImage = PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJS_ERROR_TSK);
				return chunk.hasConflictOutsideThisChunk() ? getImage(errorImage) : null;
			}
			else {
				return null;
			}
		}

		@Override
		public String getText(Object element) {
			if (element instanceof FileKey) {
				FileKey fileKey = (FileKey) element;
				return fileKey.getFileNameOnly();
			}
			else if (element instanceof Chunk) {
				Chunk chunk = (Chunk) element;
				IDocument doc = findDocumentForChunk(chunk);
				return getLabelForChunk(chunk, doc);
			}
			else {
				return super.getText(element);
			}
		}
		
		@Override
		public void dispose() {
			super.dispose();
			
			for (Image image : mImages.values()) {
				image.dispose();
			}
		}
		
		private Image getImage(ImageDescriptor imgDesc) {
			if (!mImages.containsKey(imgDesc)) {
				Image image = imgDesc.createImage();
				mImages.put(imgDesc, image);
			}
			
			return mImages.get(imgDesc);
		}
		
	}
	
	// For the top part
	private ChunksTreeViewer mChunksTreeViewer;
	// ----------------------------------------
	
	private Composite mBottomArea;
	private StackLayout mBottomStackLayout;	// the StackLayout object used for panel switching in the bottom area.
	
	// For Normal Preview Panel ---------------
	private CompareViewerSwitchingPane mPreviewPane;
	private CompareConfiguration mCompareConfiguration;
	private String mCompareTitle;
	// ----------------------------------------
	
	// For Conflict Resolution Panel ----------
	// ----------------------------------------
	
	// For Information Panel ------------------
	private Label mInformationLabel;
	// ----------------------------------------

	public InteractiveSelectiveUndoDialog(Shell parent) {
		super(parent);
		
		// Make the dialog modeless.
		setShellStyle(SWT.CLOSE | SWT.MODELESS | SWT.BORDER | SWT.TITLE | SWT.RESIZE);
		setBlockOnOpen(false);
		
		setHelpAvailable(false);
		
		// Create the default compare configuration.
		mCompareConfiguration = createCompareConfiguration();
	}

	private CompareConfiguration createCompareConfiguration() {
		CompareConfiguration configuration = new CompareConfiguration();
		configuration.setDefaultLabelProvider(new AzuriteCompareLabelProvider());
		return configuration;
	}

	@Override
	public void create() {
		super.create();
		
		instance = this;
		
		getShell().setText(TEXT);
		
		setTitle(TITLE);
		setMessage(DEFAULT_MESSAGE);
		
		getShell().setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
		
		// Register myself to the timeline.
		TimelineViewPart.getInstance().addRectSelectionListener(this);
	}

	@Override
	protected void handleShellCloseEvent() {
		cleanup();
		
		super.handleShellCloseEvent();
	}

	@Override
	protected void cancelPressed() {
		cleanup();
		
		super.cancelPressed();
	}

	@Override
	protected void okPressed() {
		cleanup();
		
		super.okPressed();
	}

	private void cleanup() {
		// Deregister myself from the timeline.
		TimelineViewPart.getInstance().removeRectSelectionListener(this);
		
		instance = null;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = createMainArea(parent);
		
		// Chunks
		createChunksTreeViewer(composite);
		
		// Bottom Area. Use StackLayout to switch between panels.
		createBottomArea(composite);
		
		return composite;
	}

	private Composite createMainArea(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		
		// Use GridLayout.
		GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.marginWidth = MARGIN_WIDTH;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = MARGIN_HEIGHT;
		gridLayout.horizontalSpacing = SPACING;
		gridLayout.verticalSpacing = SPACING;
		composite.setLayout(gridLayout);
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		return composite;
	}

	@SuppressWarnings("restriction")
	private void createChunksTreeViewer(Composite parent) {
		org.eclipse.jdt.internal.ui.util.ViewerPane chunksPane =
				new org.eclipse.jdt.internal.ui.util.ViewerPane(parent, SWT.BORDER | SWT.FLAT);
		mChunksTreeViewer = new ChunksTreeViewer(chunksPane, SWT.NONE);
		
		// Layout the chunks pane within the dialog area.
		GridData gridData = new GridData(GridData.FILL, GridData.FILL, true, true, 1, 1);
		gridData.minimumHeight = MINIMUM_CHUNKS_HEIGHT;
		chunksPane.setLayoutData(gridData);
		
		// Set the label text.
		chunksPane.setText(CHUNKS_TITLE);
		
		// Setup the toolbar.
		ToolBarManager tbm = chunksPane.getToolBarManager();
		tbm.add(new NextChunk());
		tbm.add(new PreviousChunk());
		
		tbm.update(true);
		
		// Setup the model for the tree viewer.
		mChunksTreeViewer.setContentProvider(createChunksTreeContentProvider());
		mChunksTreeViewer.setLabelProvider(createChunksTreeLabelProvider());
		mChunksTreeViewer.setComparator(createChunksTreeComparator());
		mChunksTreeViewer.addSelectionChangedListener(createSelectionChangedListener());
		
		// Set the initial input
		setChunksTreeViewerInput();
		
		// Set the content of the ViewerPane to the tree-control.
		chunksPane.setContent(mChunksTreeViewer.getControl());
	}

	private ITreeContentProvider createChunksTreeContentProvider() {
		return new ChunksContentProvider();
	}

	private ILabelProvider createChunksTreeLabelProvider() {
		return new ChunksLabelProvider();
	}

	private ViewerComparator createChunksTreeComparator() {
		return new ViewerComparator() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				Chunk lhs = (Chunk) e1;
				Chunk rhs = (Chunk) e2;
				
				return Chunk.getLocationComparator().compare(lhs, rhs);
			}
		};
	}

	private ISelectionChangedListener createSelectionChangedListener() {
		return new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection) event.getSelection();
				updateBottomPanel(sel);
			}
		};
	}
	
	private void setChunksTreeViewerInput() {
		// If the viewer is not initialized, do nothing.
		if (mChunksTreeViewer == null) {
			return;
		}
		
		TimelineViewPart timeline = TimelineViewPart.getInstance();
		// If the timeline is not open, do nothing.
		if (timeline == null) {
			return;
		}
		
		List<OperationId> ids = timeline.getRectSelection();
		
		Map<FileKey, List<RuntimeDC>> fileDCMap = RuntimeHistoryManager
				.getInstance().extractFileDCMapFromOperationIds(ids);
		
		Map<FileKey, List<Chunk>> input = new HashMap<FileKey, List<Chunk>>();
		
		for (FileKey fileKey : fileDCMap.keySet()) {
			List<Chunk> chunksForThisFile = SelectiveUndoEngine.getInstance()
					.determineChunksWithRuntimeDCs(fileDCMap.get(fileKey));
			input.put(fileKey, chunksForThisFile);
		}
		
		// Set the input here.
		mChunksTreeViewer.setInput(input);
	}

	@Override
	public void rectSelectionChanged() {
		// TODO maybe preserve the previous selections about the conflict resolution?
		setChunksTreeViewerInput();
		updateBottomPanel();
	}
	
	private void createBottomArea(Composite parent) {
		mBottomArea = new Composite(parent, SWT.NONE);
		
		// First, setup the gridlayout parameters here..
		GridData gridData = new GridData(GridData.FILL, GridData.FILL, true, true, 1, 1);
		gridData.minimumHeight = MINIMUM_BOTTOM_AREA_HEIGHT;
		mBottomArea.setLayoutData(gridData);
		
		// Use StackLayout
		mBottomStackLayout = new StackLayout();
		mBottomArea.setLayout(mBottomStackLayout);
		
		// Case #1 - Normal side-by-side preview.
		createNormalPreviewPanel(mBottomArea);
		
		// Case #2 - Need to resolve conflict
		createConflictResolutionPanel(mBottomArea);
		
		// Case #3 - Information panel, telling some useful information
		createInformationPanel(mBottomArea);
		
		// Update the bottom panel.
		updateBottomPanel();
	}
	
	private void createNormalPreviewPanel(Composite parent) {
		mPreviewPane = new CompareViewerSwitchingPane(parent, SWT.BORDER | SWT.FLAT) {
			@Override
			protected Viewer getViewer(Viewer oldViewer, Object input) {
				// TODO Auto-generated method stub
				Viewer v = CompareUI.findContentViewer(oldViewer, input, this, mCompareConfiguration);
				v.getControl().setData(CompareUI.COMPARE_VIEWER_TITLE, mCompareTitle);
				
				return v;
			}
		};
	}
	
	private void createConflictResolutionPanel(Composite parent) {
		// TODO implement
	}
	
	private void createInformationPanel(Composite parent) {
		mInformationLabel = new Label(parent, SWT.BORDER | SWT.FLAT);
		mInformationLabel.setText("Fill in some useful information here!");
	}
	
	private void showPreviewPanel(Chunk chunk) {
		if (chunk == null) {
			throw new IllegalArgumentException();
		}
		
		try {
			IDocument doc = findDocumentForChunk(chunk);
			
			// Original source
			String originalContents = doc.get(chunk.getStartOffset(), chunk.getChunkLength());
			
			// Calculate the preview using selective undo engine
			String undoResult = SelectiveUndoEngine.getInstance()
					.doSelectiveUndoChunkWithoutConflicts(chunk, originalContents);
			
			// Calculate the startline / endline from the originalContents.
			int startLine = doc.getLineOfOffset(chunk.getStartOffset());
			int endLine = doc.getLineOfOffset(chunk.getEndOffset());
			mCompareTitle = getLabelForChunk(chunk, doc);
			
			// Add surrounding context before/after the code
			int contextStartLine = Math.max(startLine - SURROUNDING_CONTEXT_SIZE, 0);
			int contextEndLine = Math.min(endLine + SURROUNDING_CONTEXT_SIZE, doc.getNumberOfLines() - 1);
			
			int contextStartOffset = doc.getLineOffset(contextStartLine);
			int contextEndOffset = doc.getLineOffset(contextEndLine) + doc.getLineLength(contextEndLine);
			
			String contextBefore = doc.get(
					contextStartOffset,
					chunk.getStartOffset() - contextStartOffset);
			String contextAfter = doc.get(
					chunk.getEndOffset(),
					contextEndOffset - chunk.getEndOffset());

			// Left, right items.
			SimpleCompareItem leftItem = new SimpleCompareItem(
					"Original Source",
					contextBefore + originalContents + contextAfter,
					false);
			SimpleCompareItem rightItem = new SimpleCompareItem(
					"Preview of Selective Undo",
					contextBefore + undoResult + contextAfter,
					false);
			
			// Build the compareInput and feed it into the compare viewer switching pane.
			AzuriteCompareInput compareInput = new AzuriteCompareInput(
					leftItem,	// Original Source
					rightItem);	// Preview Source
			
			mPreviewPane.setInput(compareInput);
			
			// Bring the preview panel to top.
			showPanel(mPreviewPane);
		}
		catch (Exception e) {
			e.printStackTrace();
			
			// Display an error message on the screen.
			String msg = "Error occurred while generating the preview.";
			mInformationLabel.setText(msg);
			showInformationPanel();
		}
	}
	
	public String getLabelForChunk(Chunk chunk, IDocument doc) {
		try {
			int startLine = doc.getLineOfOffset(chunk.getStartOffset());
			int endLine = doc.getLineOfOffset(chunk.getEndOffset());
			if (startLine == endLine) {
				return chunk.getBelongsTo().getFileNameOnly() + ": line " + startLine;
			}
			else {
				return chunk.getBelongsTo().getFileNameOnly() + ": lines " + startLine + "-" + endLine;
			}
		}
		catch (Exception e) {
			return chunk.getBelongsTo().getFileNameOnly();
		}
	}

	public IDocument findDocumentForChunk(Chunk chunk) {
		try {
			// Retrieve the IDocument, using the file information.
			FileKey fileKey = chunk.getBelongsTo();
			
			IDocument doc = findDocumentFromOpenEditors(fileKey);
			// If this file is not open, then just connect it with the relative path.
			if (doc == null) {
				IWorkspace workspace = ResourcesPlugin.getWorkspace();
				IWorkspaceRoot root = workspace.getRoot();
				
				IPath absPath = new Path(fileKey.getFilePath());
				IPath relPath = absPath.makeRelativeTo(root.getLocation());
				
				ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
				manager.connect(relPath, LocationKind.IFILE, null);
				ITextFileBuffer buffer = manager.getTextFileBuffer(relPath, LocationKind.IFILE);
				
				doc = buffer.getDocument();
			}
			return doc;
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private IDocument findDocumentFromOpenEditors(FileKey fileKey) {
		try {
			IEditorReference[] editorRefs = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage()
					.getEditorReferences();
			
			for (IEditorReference editorRef : editorRefs) {
				IEditorInput input = editorRef.getEditorInput();
				if (input instanceof IFileEditorInput) {
					IFileEditorInput fileInput = (IFileEditorInput) input;
					IFile file = fileInput.getFile();
					
					FileKey key = new FileKey(
							file.getProject().getName(),
							file.getLocation().toOSString());
					
					// This is the same file!
					// Get the IDocument object from this editor.
					if (fileKey.equals(key)) {
						return Utilities.getDocument(editorRef.getEditor(false));
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

	private void showConflictResolutionPanel() {
		// TODO implement
	}
	
	private void showInformationPanel() {
		showPanel(mInformationLabel);
	}
	
	private void showPanel(Control panel) {
		mBottomStackLayout.topControl = panel;
		mBottomArea.layout();
	}
	
	public void updateBottomPanel() {
		updateBottomPanel((IStructuredSelection)mChunksTreeViewer.getSelection());
	}

	public void updateBottomPanel(IStructuredSelection sel) {
		if (sel.size() == 1) {
			Object firstElement = sel.getFirstElement();
			
			if (firstElement instanceof Chunk) {
				Chunk chunk = (Chunk) firstElement;
				
				if (chunk.hasConflictOutsideThisChunk()) {
				// Show conflict resolution dialog here..
					// Setup the conflict resolution panel.
					
					// Show the conflict resolution panel.
					showConflictResolutionPanel();
				} else {
				// Show a normal preview..
					showPreviewPanel(chunk);
				}
			}
			else if (firstElement instanceof FileKey) {
				FileKey fileKey = (FileKey) firstElement;
//				showPreviewPanel(fileKey);
			}
		}
		else {
			@SuppressWarnings("unchecked")
			Map<FileKey, List<Chunk>> input = (Map<FileKey, List<Chunk>>) mChunksTreeViewer.getInput(); 
			boolean chunksEmpty = input == null || input.keySet().isEmpty();
			
			String msg = chunksEmpty ? INFORMATION_SELECT_RECTS
					: INFORMATION_SELECT_CHUNK;
			mInformationLabel.setText(msg);
			
			showInformationPanel();
		}
	}
	
}
