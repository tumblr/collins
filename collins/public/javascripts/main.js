/**
* Given an input element, find the next input in the form and focus it
*/
function focusNextInput(e) {
  // Use an :eq selector to find the input having an index one more than the current element
  var next = $(":input:eq(" + ($(":input").index(e) + 1) + ")");
  next.focus();
}

$(document).ready(function() {

  // Setup alert boxes to be dismissable
  $(".alert-message[data-alert]").alert();

  // focus input elements with a focus class
  $("input.focus").focus();

  // Attach a keypress handler that moves to the next input on enter
  $("input[enter-style=tab]").each(function() {
    var e = $(this);
    e.keypress(function(event) {
      if (event.which == 10 || event.which == 13) {
        event.preventDefault();
        focusNextInput(event.target);
      }
    });
  });

  $("[alt-key]").each(function() {
    var e = $(this);
    var altKey = e.attr('alt-key').toLowerCase();
    var keyLen = altKey.length;
    var soFar = "";
    $(document).keypress(function(event) {
      if (event.which == 10 || event.which == 13) {
        if (soFar.slice(soFar.length - keyLen).toLowerCase() == altKey) {
          window.location=$(e).attr('href')
        }
      } else {
        soFar = soFar + String.fromCharCode(event.which);
      }
    });
  });

  var fnLogProcessing = function (sSource, aoData, fnCallback) {
    var paging = this.fnPagingInfo();
    aoData.push( {"name": "page", "value": paging.iPage} );
    aoData.push( {"name": "size", "value": paging.iLength} );
    var sortDir = "desc";
    for (var i = 0; i < aoData.length; i++) {
      if (aoData[i].name == "sSortDir_0") {
        sortDir = aoData[i].value;
        break;
      }
    }
    aoData.push( {"name": "sort", "value": sortDir} );
    $.ajax( {
      "dataType": 'json',
      "type": "GET",
      "url": sSource,
      "data": aoData,
      "success": function(json) {
        json.iTotalRecords = json.Pagination.TotalResults;
        json.iTotalDisplayRecords = json.iTotalRecords;
        fnCallback(json);
      }
    });
  };
  var oTable = $('table.log-data[data-source]').each(function() {
    var e = $(this);
    var src = e.attr('data-source')
    e.dataTable({
      "bProcessing": true,
      "bServerSide": true,
      "bFilter": false,
      "sAjaxSource": src,
      "aaSorting": [[0, "desc"]],
      "sPaginationType": "bootstrap",
      "sDom": "<'row'<'span7'l><'span7'f>r>t<'row'<'span7'i><'span7'p>>",
      "iDisplayLength": 25,
      "fnServerData": fnLogProcessing,
      "sAjaxDataProp": "Data",
      "aoColumns": [
        { "mDataProp": "Created" },
        { "mDataProp": "IsError", "bSearchable": false, "bSortable": false },
        { "mDataProp": "Message", "bSortable": false }
      ]
    });
  });

})
