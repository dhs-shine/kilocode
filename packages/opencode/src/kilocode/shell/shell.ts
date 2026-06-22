export function args(command: string) {
  return ["-NoLogo", "-NoProfile", "-NonInteractive", "-Command", script(command)]
}

function script(command: string) {
  return `[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false);
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false);
$OutputEncoding = [Console]::OutputEncoding;
${command}`
}
