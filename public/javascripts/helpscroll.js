(function() {

  var body = $('body');
  var sidenav = $('#sidenav');

  sidenav.attr('data-spy', 'affix');
  sidenav.attr('data-offset-top', '290');
  sidenav.attr('data-offset-bottom', '270');

  body.attr('data-spy', 'scroll');
  body.attr('data-target', '#sidebar');


})();
