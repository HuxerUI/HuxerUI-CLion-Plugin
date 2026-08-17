#include <huxerui/huxerui.h>
#include <template_module/template_module.h>

using namespace huxerui;

View App() {
  return MaterialTheme([] {
    return Text("Template-Module preview");
  });
}

const Application application{
    App,
    {
        .window = {
            .title = "Template-Module Preview",
        },
        .root_hooks = {
            template_module::Install,
        },
    }
};
