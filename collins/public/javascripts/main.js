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


})
