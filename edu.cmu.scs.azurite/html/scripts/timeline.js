/**
 * Things should be executed at the beginning.
 */

// Disable selection.
document.unselectable = "on";
document.onselectstart = function () { return false };


/**
 * Constants. (Always use UPPER_CASE.)
 */
var MENU_PANEL_HEIGHT = 75;

var TYPE_INSERT = 0;
var TYPE_DELETE = 1;
var TYPE_REPLACE = 2;

var NUM_TIMESTAMPS = 3;

var MIN_WIDTH = 5;
var ROW_HEIGHT = 30;
var TICKS_HEIGHT = 20;
var DEFAULT_RATIO = 1000;

var FILE_NAME_OFFSET_X = 0;
var FILE_NAME_OFFSET_Y = 5;

var FILES_PORTION = 0.15;

var HIGHLIGHT_WIDTH = 3;

var CHART_MARGINS = {
    left: 15,
    top: 15,
    right: 15,
    bottom: 15
};


// mapping functions
var rectDraw = {};
rectDraw.xFunc = function (d) { return (d.sid + d.t1 - global.startTimestamp) / DEFAULT_RATIO; };
rectDraw.yFunc = function (d) { return Math.min(ROW_HEIGHT * d.y1 / 100, ROW_HEIGHT - MIN_WIDTH / global.scaleY); };
rectDraw.wFunc = function (d) { return Math.max(MIN_WIDTH / global.scaleX, (d.t2 - d.t1) / DEFAULT_RATIO); };
rectDraw.hFunc = function (d) { return Math.max(MIN_WIDTH / global.scaleY, ROW_HEIGHT * (d.y2 - d.y1) / 100); };

var fileDraw = {};
fileDraw.yFunc = function (d, i) { return ROW_HEIGHT * (i + global.translateY) * global.scaleY + FILE_NAME_OFFSET_Y; };

var lineDraw = {};
lineDraw.x2Func = function (d) { return getSvgWidth() * (1.0 - FILES_PORTION); };
lineDraw.yFunc = function(d, i) { return ROW_HEIGHT * (i + global.translateY) * global.scaleY };

/**
 * Global variables. (Always use a pseudo-namespace.)
 */
var global = {};

// variables to remember the last window size
global.lastWindowWidth = null;
global.lastWindowHeight = null;

global.files = [];
global.selected = [];

// transforms
global.translateX = 0;
global.translateY = 0;
global.scaleX = 1;
global.scaleY = 1;

// last file opened
global.currentFile = null;
global.lastOperation = null;

// Timestamps
global.maxTimestamp = 0;
global.startTimestamp = new Date().valueOf();

// Dragging
global.dragging = false;
global.dragStart = [];
global.draggableArea = {
    left: 0,
    top: 0,
    right: 0,
    bottom: 0
};


// context menu
var cmenu = {};
cmenu.isContextMenuVisible = false;
cmenu.isRightButtonDown = false;
cmenu.isCtrlDown = false;


/**
 * SVG Setup.
 */
var svg = {};

function setupSVG() {
	svg.main = d3.select('#svg_wrapper')
		.append('svg')
		.attr('class', 'svg')

	svg.subFiles = svg.main
		.append('g')
		.attr('id', 'sub_files')
        .attr('clip-path', 'url(#clipFiles)');
		
	svg.subRectsWrap = svg.main
		.append('g')
		.attr('id', 'sub_rects_wrap')
        .attr('clip-path', 'url(#clipRectsWrap)');
    
    svg.subRects = svg.subRectsWrap
        .append('g')
        .attr('id', 'sub_rects');
		
	svg.subTicks = svg.main
		.append('g')
		.attr('id', 'sub_ticks');

	svg.main.append('rect')
		.attr('class', 'selection_box')
		.style('fill', 'yellow')
		.style('opacity', 0.3);
        
    svg.clipFiles = svg.main
        .append('clipPath')
        .attr('id', 'clipFiles')
        .append('rect');
    
    svg.clipRectsWrap = svg.main
        .append('clipPath')
        .attr('id', 'clipRectsWrap')
        .append('rect');
        
    recalculateClipPaths();
}

function recalculateClipPaths() {
    var svgWidth = getSvgWidth();
    var svgHeight = getSvgHeight();
    
    svg.clipFiles
        .attr('width', (svgWidth * FILES_PORTION) + 'px')
        .attr('height', (svgHeight - 20) + 'px');
    
    svg.subFiles
        .attr('transform', 'translate(' + CHART_MARGINS.left + ' ' + CHART_MARGINS.top + ')');
    
    svg.subRectsWrap
        .attr('transform', 'translate(' + (CHART_MARGINS.left + svgWidth * FILES_PORTION) + ' ' + CHART_MARGINS.top + ')');
        
    svg.clipRectsWrap
        .attr('y', '-1')
        .attr('width', (svgWidth * (1.0 - FILES_PORTION)) + 'px')
        .attr('height', (svgHeight - TICKS_HEIGHT + 2) + 'px');
    
    svg.subTicks
        .attr('transform', 'translate(' + (CHART_MARGINS.left + svgWidth * FILES_PORTION) + ' ' + (CHART_MARGINS.top + svgHeight - TICKS_HEIGHT) + ')');
}

/**
 * An object that keeps track of insert, delete and replace for each file.
 */
function File(path, fileName) {
    this.path = path;
    this.fileName = fileName;
    this.operations = [];
    
    this.g = svg.subRects.append('g');
}

function OperationId(sid, id) {
    this.sid = sid;
    this.id = id;
}

/**
 * An object for representing an operation itself.
 */
function EditOperation(sid, id, t1, t2, y1, y2, type) {
    this.sid = sid;
    this.id = id;
    this.t1 = t1;
    this.t2 = (t2 == null ? t1 : t2);
    this.y1 = y1;
    this.y2 = y2;
    this.type = type;
    this.color;
    
    if(type == TYPE_INSERT) {
        this.color = "green";
    } else if(type == TYPE_DELETE) {
        this.color = "red";
    } else if(type == TYPE_REPLACE) {
        this.color = "blue";
    }
}

/**
 * Called by Azurite.
 * Adds a new row if the given file is not already in the list.
 */
function addFile(path) {
    var fileName = path.match(/[^\\/]+$/)[0];
    
    for(var index in global.files) {
        if(global.files[index].path == path) {
            global.currentFile = global.files[index];
            return;
        }
    }
    
    var newFile = new File(path, fileName);
    newFile.g.attr('transform', 'translate(0 ' + (global.files.length * ROW_HEIGHT) + ')');
    
    global.files.push(newFile);
    global.currentFile = newFile;
    
    svg.subFiles.selectAll('text').data(global.files)
        .enter()
        .append('text')
        .attr('x', FILE_NAME_OFFSET_X + 'px')
        .attr('y', fileDraw.yFunc)
        .attr('dy', '1em')
        .attr('fill', 'white')
        .text(function (d) { return d.fileName; });
    
    // Draw separating lines
    if ( global.files.length == 1) {
        addSeparatingLine();
    }
    
    addSeparatingLine();
    
    updateSeparatingLines();
}

function addSeparatingLine() {
    svg.subRectsWrap.append('line')
        .attr('class', 'separating_line')
        .attr('x1', '0')
        .attr('y1', lineDraw.yFunc)
        .attr('x2', lineDraw.x2Func)
        .attr('y2', lineDraw.yFunc)
        .attr('stroke', 'gray')
        .attr('stroke-width', '2');
}

function updateSeparatingLines() {
    svg.subRectsWrap.selectAll('line.separating_line')
        .attr('y1', lineDraw.yFunc)
        .attr('x2', lineDraw.x2Func)
        .attr('y2', lineDraw.yFunc);
}

/**
 * Called by Azurite.
 * Sets the start timestamp.
 */
function setStartTimestamp(timestamp) {
    global.startTimestamp = parseInt(timestamp);
    drawRules();
}


/**
 * Called by Azurite.
 * Add an edit operation to the end of the file.
 * Note that this is called immediately after an edit operation is performed.
 */
function addOperation(sid, id, t1, t2, y1, y2, type) {
    var newOp = new EditOperation(
        parseInt(sid),
        parseInt(id),
        parseInt(t1),
        parseInt(t2),
        parseFloat(y1),
        parseFloat(y2),
        parseInt(type)
    );

    var i;
    var fileIndex = -1;
    
    for(i = 0; i < global.files.length; i++) {
        if(global.files[i].path == global.currentFile.path) {
            fileIndex = i;
            break;
        }
    }
    
    if(fileIndex == -1)
        return;
    
    global.currentFile.operations.push(newOp);
    global.lastOperation = newOp;
    
    updateMaxTimestamp(t1, t2);
    
    global.lastRect = global.currentFile.g.selectAll('rect').data( global.currentFile.operations )
        .enter()
        .append('rect')
        .attr('id', function (d) { return d.sid + '_' + d.id; })
        .attr('class', 'op_rect')
        .attr('x', rectDraw.xFunc)
        .attr('y', rectDraw.yFunc)
        .attr('width', rectDraw.wFunc)
        .attr('height', rectDraw.hFunc)
        .attr('fill', function (d) { return d.color; })
        .attr('vector-effect', 'non-scaling-stroke');
}

/**
 * Called by Azurite.
 * Update the timestamp2 value for an existing operation, in case multiple
 * operations are merged into one.
 */
function updateOperationTimestamp2(id, t2) {
    if (global.lastOperation == null || global.lastOperation.id != parseInt(id))
        return;
    
    global.lastOperation.t2 = t2;
    
    updateMaxTimestamp(t2, t2);
    
    if (global.lastRect != null) {
        global.lastRect.attr('width', rectDraw.wFunc);
    }
}

function redraw() {
    var svgWidth = parseInt(svg.main.style('width'));
    var svgHeight = parseInt(svg.main.style('height'));
    
    recalculateClipPaths();
    
    svg.subRects.selectAll('rect').remove();
    
    // remove highlights
    global.selected = [];
    svg.subRects.selectAll('use.highlight_rect').remove();
    
    $('.block').tipsy({ 
        gravity: 'se', 
        html: true, 
        title: function() {
              var d = this.__data__;
              return 'id: ' + d.id;
        }
    });
}

/*
 * When the page loads, load a log file
 */
window.onload = function () {
    azurite.initialize();
    
	setupSVG();
    initContextMenu();
    
    window.onresize();
    initEventHandlers();
    
    test();
}

window.onresize = function (e) {
    // if window size are different, redraw everything
    if(global.lastWindowWidth != window.innerWidth || global.lastWindowHeight != window.innerHeight) {
        global.lastWindowWidth = window.innerWidth;
        global.lastWindowHeight = window.innerHeight;
        
        recalculateClipPaths();
        updateSeparatingLines();
        updateDraggableArea();
    }
}

function updateDraggableArea() {
    global.draggableArea.left = CHART_MARGINS.left + getSvgWidth() * FILES_PORTION;
    global.draggableArea.top = CHART_MARGINS.top;
    global.draggableArea.right = getSvgWidth() - CHART_MARGINS.right;
    global.draggableArea.bottom = getSvgHeight() - CHART_MARGINS.bottom;
}

/******************************************************************
 MOUSE EVENT FUNCTIONS
 ******************************************************************/
 
function initContextMenu() {
    global.divContext = document.getElementById('context_menu');
    
    global.divContext.onmouseover = function() { mouseOverContext = true; };
    global.divContext.onmouseout = function(e) {
        e = event.toElement || event.relatedTarget;
        
        while(e && e.parentNode && e.parentNode != window) {
            if(e.parentNode == this || e == this) {
                return;
            }
            e = e.parentNode;
        }
        
        // hideContextMenu();
    };
}

function initEventHandlers() {
    svg.main.on("mousewheel", function () {
        scrollRight( d3.event.wheelDelta / 10 );
    });
    
    document.addEventListener("keydown", function(e) {
        if(e.keyCode == 17) 
            cmenu.isCtrlDown = true;
    }
    , false);
    
    document.addEventListener("keyup", function(e) {
        if(e.keyCode == 17)
            cmenu.isCtrlDown = false;
    }
    , false);
    
    svg.main.on('mousedown', function (e) {
        if(cmenu.isContextMenuVisible) {
            hideContextMenu();
        }
        
        if ("which" in event) { // Gecko (Firefox), WebKit (Safari/Chrome) & Opera
            cmenu.isRightButtonDown = event.which == 3; 
        } else if ("button" in event) { // IE, Opera 
            cmenu.isRightButtonDown = event.button == 2; 
        }
        
        var mouseX = d3.mouse(this)[0];
        var mouseY = d3.mouse(this)[1];
        
        if(cmenu.isRightButtonDown || mouseX < global.draggableArea.left || mouseX > global.draggableArea.right || mouseY < global.draggableArea.top || mouseY > global.draggableArea.bottom) {
            return;
        }
        
        if(global.dragging)
            return;
        
        global.dragging = true;
        
        if(!cmenu.isCtrlDown) {
            global.selected = [];
            svg.subRects.selectAll('use.highlight_rect').remove();
        }
        
        d3.select('.selection_box')
            .attr('x', mouseX)
            .attr('y', mouseY);
        
        global.dragStart[0] = mouseX;
        global.dragStart[1] = mouseY;
    });

    svg.main.on('mousemove', function (e) {
        if(!global.dragging)
            return;
        
        var newX, newY;
        
        var mouseX = d3.mouse(this)[0];
        var mouseY = d3.mouse(this)[1];
        
        if(mouseX < global.draggableArea.left)
            newX = global.draggableArea.left;
        else if(mouseX > global.draggableArea.right) 
            newX = global.draggableArea.right;
        else
            newX = mouseX;
            
        if(mouseY < global.draggableArea.top)
            newY = global.draggableArea.top;
        else if(mouseY > global.draggableArea.bottom)
            newY = global.draggableArea.bottom;
        else
            newY = mouseY;
        
        
        if(newX - global.dragStart[0] < 0) {
            d3.select('.selection_box')
                .attr('x', newX)
                .attr('width', global.dragStart[0] - newX);
        } else {
            d3.select('.selection_box')
                .attr('x', global.dragStart[0])
                .attr('width', newX - global.dragStart[0]);
        }
        
        if(newY - global.dragStart[1] < 0) {
            d3.select('.selection_box')
                .attr('y', newY )
                .attr('height', global.dragStart[1] - newY);
        } else {
            d3.select('.selection_box')
                .attr('y', global.dragStart[1])
                .attr('height', newY - global.dragStart[1]);
        }
        
        d3.select('.selection_box')
            .attr('display', 'block');
    });
    
    svg.main.on('mouseup', function (e) {
        if(cmenu.isRightButtonDown) {
            showContextMenu(d3.event);
            return;
        }
    
        if(!global.dragging)
            return;
        
        d3.select('.selection_box')
            .attr('display', 'none');
        
        var x1, y1, x2, y2;
        
        var mouseX = d3.mouse(this)[0];
        var mouseY = d3.mouse(this)[1];
    
        if(global.dragStart[0] <= mouseX) {
            x1 = global.dragStart[0];
            x2 = mouseX;
        } else {
            x1 = mouseX;
            x2 = global.dragStart[0];
        }
        
        if(global.dragStart[1] <= mouseY) {
            y1 = global.dragStart[1];
            y2 = mouseY;
        } else {
            y1 = mouseY;
            y2 = global.dragStart[1];
        }
        addSelections(x1, y1, x2, y2);
    
        global.dragging = false;   
        global.dragStart = [];
    });
}
 
function showContextMenu(event) {   

    var offsetX = 0, offsetY = 0;
    
    var $contextMenu = $('#context_menu');
    var w = $contextMenu.outerWidth();
    var h = $contextMenu.outerHeight();
    
    global.divContext.style.left = Math.min(event.clientX, global.lastWindowWidth - w) + 'px';
    global.divContext.style.top = Math.min(event.clientY, global.lastWindowHeight - h) + 'px';
    global.divContext.style.display = 'block';
    
    cmenu.isContextMenuVisible = true;
}

function hideContextMenu() {
    global.divContext.style.display = 'none';
    cmenu.isContextMenuVisible = false;
}


function addSelectionsByIds(sids, ids, clearPreviousSelection) {
    if (clearPreviousSelection) {
        global.selected = [];
    }
    
    for (var i = 0; i < ids.length; ++i) {
        var sid = sids[i];
        var id = ids[i];
        global.selected.push(new OperationId(sid, id));
    }
    
    updateHighlight();
}


function addSelections(x1, y1, x2, y2) {
    var somethingAdded = false;

    var rect = svg.main.node().createSVGRect();
    rect.x = x1 + 5;    // the value from #svg_wrapper.padding
    rect.y = y1 + 5;
    rect.width = Math.max(x2 - x1, 1);
    rect.height = Math.max(y2 - y1, 1);
    
    // Get all the intersecting objects in the SVG.
    var list = svg.main.node().getIntersectionList(rect);
    
    // Filter only the operation rects.
    d3.selectAll(list).filter('.op_rect').each( function (d, i) {
        var sid = d.sid;
        var id = d.id;
        
        if (!isSelected( sid, id )) {
            global.selected.push(new OperationId(sid, id));
        }
    });
    
    updateHighlight();
}

function isSelected(sid, id) {
    var i;
    for (i = 0; i < global.selected.length; ++i) {
        if (global.selected[i].sid == sid && global.selected[i].id == id) {
            return true;
        }
    }
    
    return false;
}

function updateHighlight() {
    svg.subRects.selectAll('use.highlight_rect').remove();
    
    for (var i = 0; i < global.selected.length; ++i) {
        var idString = '#' + global.selected[i].sid + '_' + global.selected[i].id;
        
        d3.select( $(idString)[0].parentNode ).insert('use', ':first-child')
            .attr('xlink:href', idString)
            .attr('class', 'highlight_rect')
            .attr('stroke', 'yellow')
            .attr('stroke-width', (HIGHLIGHT_WIDTH * 2) + 'px')
            .attr('fill-opacity', '0');
    }
}

    
/******************************************************************
 LISTENER FUNCTIONS
 ******************************************************************/
function barZoomIn() {
    scaleX( global.scaleX + 0.1 );
}

function barZoomOut() {
    scaleX( global.scaleX - 0.1 );
}

function showBefore() {
    scrollRight( 100 );
}

function showAfter() {
    scrollRight( -100 );
}

function scrollRight(pixels) {
    translateX( global.translateX + pixels );
}

function fileZoomIn() {
    scaleY( global.scaleY + 0.5 );
}

function fileZoomOut() {
    scaleY( global.scaleY - 0.5 );
}

function showUp() {
    translateY( global.translateY + 1 );
}

function showDown() {
    translateY( global.translateY - 1 );
}


function undo() {
    // close context menu if there is any
    hideContextMenu();
    var result = [];
    
    for(var i in global.selected) {
        result.push(global.selected[i]);
    }
    
    if(result.length > 0)
        azurite.selectiveUndo(result);
}
    

function updateMaxTimestamp(timestamp, timestamp2) {
    // update global.maxTimestamp if necessary
    if (timestamp2 == null && timestamp > global.maxTimestamp) {
        global.maxTimestamp = timestamp;
    } else if (timestamp2 != null && timestamp2 > global.maxTimestamp) {
        global.maxTimestamp = timestamp2;
    }
}

function clamp(value, min, max) {
    if (value < min) {
        value = min;
    }
    if (value > max) {
        value = max;
    }
    
    return value;
}

function range(begin, end) {
    var result = new Array();
    for (var i = begin; i < end; ++i) {
        result[i - begin] = i;
    }
    
    return result;
}

function scaleX(sx) {
    sx = clamp( sx, 0.1, 50 );
    global.translateX = global.translateX / global.scaleX * sx;
    global.scaleX = sx;
    
    updateSubRectsTransform();
    
    svg.subRects.selectAll('rect')
        .attr('width', rectDraw.wFunc);
}

function scaleY(sy) {
    sy = clamp( sy, 0.1, 10 );
    global.scaleY = sy;
    
    updateSubRectsTransform();
    
    svg.subRects.selectAll('rect')
        .attr('height', rectDraw.hFunc);
        
    svg.subFiles.selectAll('text')
        .attr('y', fileDraw.yFunc);
    
    updateSeparatingLines();
}

function translateX(tx) {
    tx = clamp( tx, -global.maxTimestamp / DEFAULT_RATIO, 0 );
    global.translateX = tx;
    
    updateSubRectsTransform();
}

function translateY(ty) {
    ty = clamp( ty, 1 - global.files.length, 0 );
    global.translateY = ty;
    
    updateSubRectsTransform();
        
    svg.subFiles.selectAll('text')
        .attr('y', fileDraw.yFunc);
    
    updateSeparatingLines();
}

function updateSubRectsTransform() {
    svg.subRects
        .attr('transform',
            'translate(' + global.translateX + ' ' + (global.translateY * ROW_HEIGHT * global.scaleY) + ') ' +
            'scale(' + global.scaleX + ' ' + global.scaleY + ')');
}

function showFrom(timestamp) {
    var translateX = -(timestamp - global.startTimestamp) / DEFAULT_RATIO;
}

function test() {
	addFile('Test.java');
	addRandomOperations(100);
    
    addFile('Test2.java');
    addRandomOperations(200);
    
	addFile('Test.java');
	addRandomOperations(100);
}

function addRandomOperations(count) {
	var i = 0;
	var id = -1;
	var t = 0;
	
	if (global.lastOperation != null) {
		id = global.lastOperation.id;
        t = global.lastOperation.t2;
	}
	
	for (i = 0; i < count; ++i) {
		++id;
		var t1 = t + Math.floor(Math.random() * 25000) + 5000;
		var t2 = t1 + Math.floor(Math.random() * 5000) + 5000;
		t = t2;
        
        var y1 = Math.floor(Math.random() * 100);
        var y2 = clamp(y1 + Math.floor(Math.random() * 30), y1, 100);
		addOperation(global.startTimestamp, id, t1, t2, y1, y2, Math.floor(Math.random() * 3));
	}
}

function getSvgWidth() {
    return parseInt(svg.main.style('width')) - CHART_MARGINS.left - CHART_MARGINS.right;
}

function getSvgHeight() {
    return parseInt(svg.main.style('height')) - CHART_MARGINS.top - CHART_MARGINS.bottom;
}
