import "@kilocode/kilo-web-ui/styles"
import { Router, Route } from "@solidjs/router"
import { render } from "solid-js/web"
import App from "./App"
import "./styles.css"
import { ProjectConsoleRoute } from "./routes/projects/ProjectConsoleRoute"
import { ProjectsRoute } from "./routes/projects/ProjectsRoute"
import { ProfileRoute } from "./routes/profile/ProfileRoute"
import { ConfigLayout } from "./layouts/ConfigLayout"
import { configSections } from "./routes/config/sections"

const root = document.getElementById("root")
if (!root) throw new Error("Missing root element")

const base = import.meta.env.BASE_URL.replace(/\/$/, "")

// Register the "Kilo" Pierre/shiki theme required by the diff review components
// (Code/SessionReview). Without it the diff worker throws "resolveTheme: No valid
// loader for Kilo" when a file is expanded. Loaded out-of-band so katex/marked stay
// out of the initial bundle; it resolves long before a diff can be opened.
void import("@opencode-ai/ui/context/marked")

function routes() {
  return configSections.map((item) => <Route path={item.path} component={item.component} />)
}

render(
  () => (
    <Router root={App} base={base || undefined}>
      <Route path="/projects" component={ProjectsRoute} />
      <Route path="/projects/:project" component={ProjectConsoleRoute} />
      <Route path="/projects/:project/settings" component={ConfigLayout}>
        {routes()}
      </Route>
      <Route path="/profile" component={ProfileRoute} />
      <Route path="/settings" component={ConfigLayout}>
        {routes()}
      </Route>
      <Route path="/config" component={ConfigLayout}>
        {routes()}
      </Route>
      <Route path="*" component={ProjectsRoute} />
    </Router>
  ),
  root,
)
